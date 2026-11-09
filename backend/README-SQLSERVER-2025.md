# Environnement candidat SQL Server 2025

Cet environnement sert à vérifier le backend avec SQL Server 2025 et le pilote JDBC 13.6.0. Il est séparé du Compose principal et de la POC :

- conteneur : `assistia-sqlserver-2025-candidate` ;
- port hôte : `127.0.0.1:1434` ;
- volume explicitement nommé : `assistia_sqlserver2025_candidate_data` ;
- profil Spring : `sqlserver2025`, avec `vectorTypeSupport=v2` uniquement sur cette URL JDBC.

Le volume SQL Server 2022 du Compose principal et celui de la POC ne sont pas référencés ici. Ne jamais modifier, renommer ou monter ces volumes pour cet environnement.

## Préparer le secret local

Depuis la racine du dépôt, créer un mot de passe local fort. Le fichier est ignoré par Git grâce à `.env.*` :

```sh
umask 077
python3 -c "from pathlib import Path; import secrets; p = Path('.env.sqlserver2025'); p.exists() or p.write_text('SPRING_DATASOURCE_PASSWORD=' + secrets.token_urlsafe(32) + '\n')"
```

Le même secret sert au compte SQL `sa` et au backend local. Ne pas le copier dans un fichier versionné ni le partager.

## Démarrer et vérifier SQL Server 2025

Depuis la racine :

```sh
docker compose --env-file .env.sqlserver2025 -p assistia-sqlserver2025-candidate -f docker-compose.sqlserver2025.yml up -d --wait
docker compose --env-file .env.sqlserver2025 -p assistia-sqlserver2025-candidate -f docker-compose.sqlserver2025.yml ps
```

Vérifier la version du moteur :

```sh
set -a
. ./.env.sqlserver2025
set +a
docker exec -e SQLCMDPASSWORD="$SPRING_DATASOURCE_PASSWORD" assistia-sqlserver-2025-candidate \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -Q "SELECT @@VERSION;"
```

La première initialisation ne crée pas de base métier. Avant le premier démarrage backend, créer une base vide nommée `AssistIA` sur cette instance seulement :

```sh
docker exec -e SQLCMDPASSWORD="$SPRING_DATASOURCE_PASSWORD" assistia-sqlserver-2025-candidate \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C \
  -Q "IF DB_ID(N'AssistIA') IS NULL EXEC('CREATE DATABASE [AssistIA]');"
```

Cela ne restaure aucune donnée. Au premier démarrage, Flyway applique V1 à V4 à cette base vide ; Hibernate valide ensuite le schéma avec `ddl-auto=validate`. Les migrations V1 à V4 ne sont pas modifiées. Ne lancez pas ce profil contre la base SQL Server 2022.

## Démarrer le backend contre l'instance candidate

Dans un terminal à la racine du dépôt :

```sh
set -a
. ./.env.sqlserver2025
set +a
cd backend
SERVER_PORT=18080 mvn spring-boot:run -Dspring-boot.run.profiles=sqlserver2025
```

Le port applicatif `18080` évite le conflit avec le backend principal. Le démarrage valide la connexion JDBC et la propriété `vectorTypeSupport=v2`, puis effectue les migrations Flyway uniquement dans la base candidate. Le profil force `ddl-auto=validate`. L'application utilise les variables d'environnement pour ses identifiants ; aucune clé Gemini n'est nécessaire pour cette vérification.

Le RAG est désactivé par défaut (`assistia.rag.enabled=false`). Le modèle génératif Gemini et le modèle d'embedding sont des paramètres distincts.

Pour forcer explicitement le profil à une autre URL, fournir `SPRING_DATASOURCE_URL`, avec `vectorTypeSupport=v2` uniquement pour cette instance candidate. Ne passez pas ce paramètre à l'instance SQL Server 2022.

## Arrêt et retour à SQL Server 2022

Arrêter uniquement l'environnement candidat, sans supprimer son volume :

```sh
docker compose --env-file .env.sqlserver2025 -p assistia-sqlserver2025-candidate -f docker-compose.sqlserver2025.yml down
```

Le Compose principal et SQL Server 2022 restent indépendants. Pour revenir au fonctionnement normal, arrêter le processus backend candidat avec `Ctrl+C`, puis utiliser les commandes habituelles du Compose principal, sans profil `sqlserver2025`. Ne pas ajouter `-v` à `docker compose down` : le volume candidat doit être conservé, tout comme les volumes existants.

Le profil candidat cible par défaut `localhost:1434`; le profil SQL Server de test existant et la configuration principale SQL Server 2022 ne contiennent pas `vectorTypeSupport`.

## Phase 4A — embeddings documentaires

`KnowledgeDocumentEmbeddingService` traite une version candidate dont le statut est `AWAITING_EMBEDDINGS`. Il lit
ses fragments dans l'ordre, appelle `EmbeddingProvider` séquentiellement, puis valide que chaque fragment a un
vecteur `FLOAT32` de 1 536 dimensions et des métadonnées cohérentes. Les embeddings existants correctement indexés
sont conservés; une exécution interrompue peut reprendre les fragments manquants sans recalculer les autres.

`GeminiEmbeddingProvider` utilise le `Client` Gemini partagé avec l'analyse des tickets, `gemini-embedding-2`,
`embedContent` et une dimension de sortie explicitement fixée à 1 536. Les données de fragments ne sont jamais
journalisées. Les erreurs 408, 429, 5xx et timeout reçoivent au plus trois tentatives avec délai exponentiel et
jitter. Les erreurs permanentes et les réponses invalides ne sont pas reprises. Un échec laisse la version en
`AWAITING_EMBEDDINGS`, qui peut être relancée.

`KnowledgeChunkVectorRepository` est le seul accès à la colonne vectorielle : il utilise JDBC, `microsoft.sql.Vector`,
`VectorDimensionType.FLOAT32` et `PreparedStatement#setObject(..., microsoft.sql.Types.VECTOR)`. Le mapping Hibernate
de `VECTOR` est volontairement absent. Les métadonnées du vecteur et le vecteur sont mis à jour ensemble. Une
exécution complète passe l'état à `READY_TO_PUBLISH`; elle ne modifie jamais `current_version` et ne publie pas la
version. Il n'existe pas encore d'endpoint ou d'ordonnanceur pour déclencher le traitement.

Le RAG doit être activé explicitement (`ASSISTIA_RAG_ENABLED=true`). Les paramètres dédiés, indépendants du modèle
génératif des tickets, sont :

| Propriété | Variable d'environnement | Défaut |
| --- | --- | --- |
| `assistia.embedding.model` | `ASSISTIA_EMBEDDING_MODEL` | `gemini-embedding-2` |
| `assistia.embedding.output-dimensions` | `ASSISTIA_EMBEDDING_OUTPUT_DIMENSIONS` | `1536` |

La dimension est contrainte à 1 536 pour correspondre au schéma `VECTOR(1536)`. La clé reste `GEMINI_API_KEY`.
Vérifier les conditions de quota, coûts et traitement des données du projet Google avant d'envoyer des documents
opérationnels. Les tests automatisés n'appellent jamais Gemini et utilisent des textes/vecteurs synthétiques.

Pour lancer les tests unitaires ciblés :

```sh
cd backend
mvn -q -Dtest=GeminiEmbeddingProviderTest,KnowledgeDocumentEmbeddingServiceTest test
```

Le test SQL Server 2025 opt-in `KnowledgeEmbeddingSqlServerTest` utilise uniquement la base jetable
`AssistIAV6Test`, jamais la base principale `AssistIA`. Démarrer une instance candidate SQL Server 2025 et configurer
`ASSISTIA_SQLSERVER_V6_TEST_URL`, `ASSISTIA_SQLSERVER_V6_TEST_USERNAME` et `ASSISTIA_SQLSERVER_V6_TEST_PASSWORD`, puis :

```sh
ASSISTIA_SQLSERVER_V6_TESTS=true mvn -q -Dtest=KnowledgeEmbeddingSqlServerTest test
```

Les limites actuelles sont le traitement séquentiel sans contrôle de débit global, l'absence de réservation
inter-instance du document candidat, et l'absence du mécanisme de publication. Ne pas lancer deux indexations de la
même version simultanément.
