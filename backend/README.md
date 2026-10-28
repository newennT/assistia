# AssistIA — API tickets et authentification

## Configurer l'analyse Gemini avec Docker Compose

Docker Compose lit les variables `${GEMINI_API_KEY}` depuis l'environnement du shell ou le fichier `.env` à la
racine du dépôt. Les fichiers `.env.example` et `backend/.env` ne sont pas chargés automatiquement. Créer le fichier
attendu à partir de l'exemple, renseigner la clé, puis recréer le backend :

```sh
cp .env.example .env
```

Après avoir remplacé la valeur de `GEMINI_API_KEY` dans `.env` :

```sh
docker compose up -d --force-recreate backend
```

Le backend reçoit la clé au démarrage via `docker-compose.yml`. Pour une exécution sans Docker, définir
`GEMINI_API_KEY` dans l'environnement du processus Java. Ne pas mettre une vraie clé dans un fichier `.env.example`.

## Authentification par session

L'API utilise Spring Security et des sessions HTTP, pas de JWT. Les mots de passe sont stockés sous forme de hash
BCrypt. Les réponses d'authentification et les DTO utilisateur ne contiennent ni mot de passe ni hash.

| Méthode | Chemin | Accès | Opération |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | Public | Initialise et retourne le jeton CSRF pour le client SPA |
| `POST` | `/api/auth/login` | Public + CSRF | Authentifie `{ "email": "...", "password": "..." }` et ouvre une session |
| `GET` | `/api/auth/me` | Authentifié | Retourne le compte de la session |
| `POST` | `/api/auth/logout` | Authentifié + CSRF | Invalide la session |
| `POST` | `/api/admin/users` | `ADMIN` + CSRF | Crée un compte `USER` ou `TECHNICIAN` |
| `GET` | `/api/admin/users/{id}` | `ADMIN` | Récupère un compte géré |

Il n'existe pas d'inscription publique. Seul un administrateur connecté peut provisionner les comptes `USER` et
`TECHNICIAN`; la création d'autres `ADMIN` par cette API est interdite. Le premier administrateur est créé par le
mécanisme interactif décrit ci-dessous.

### Flux d'une SPA

1. Appeler `GET /api/auth/csrf` avec les cookies activés (`credentials: "include"`). La réponse contient `headerName`
   et `token`; le serveur pose aussi le cookie `XSRF-TOKEN`.
2. Envoyer ce cookie et le jeton dans l'en-tête `X-XSRF-TOKEN` pour `POST /api/auth/login`.
3. La connexion change l'identifiant de session et invalide le jeton CSRF précédent. Rappeler `/api/auth/csrf`,
   puis conserver le cookie `JSESSIONID` pour les requêtes suivantes.
4. Envoyer de nouveau le cookie CSRF et `X-XSRF-TOKEN` pour chaque requête non sûre (`POST`, `PUT`, `PATCH`).
   La déconnexion est elle aussi protégée contre CSRF.

Le cookie de session est `HttpOnly`, `SameSite=Lax`; seul le cookie CSRF est lisible par JavaScript. Pour la
production HTTPS, définir `ASSISTIA_COOKIE_SECURE=true`. Configurer `ASSISTIA_FRONTEND_ORIGIN` avec l'origine
exacte de la SPA, sans joker (plusieurs origines peuvent être séparées par des virgules). CORS autorise les
credentials et uniquement les méthodes/en-têtes utilisés par l'API.

### Initialiser le premier administrateur

Après le démarrage de l'application et l'application des migrations, lancer le mode bootstrap dans un terminal
interactif :

```sh
docker compose up -d --build backend
docker compose exec -it backend java -jar /app/app.jar --assistia.bootstrap-admin
```

La première commande reconstruit et recrée le conteneur backend avec le correctif courant; elle ne supprime pas les
données SQL Server. La seconde lance le CLI interactif dans ce conteneur. Saisir le mot de passe aux invites : ne pas
le passer en argument ni l'inscrire dans une variable de commande.

En exécution locale, construire le JAR puis lancer la même option en ayant configuré les variables de connexion
SQL Server :

```sh
cd backend
mvn package
java -jar target/assistia-backend-0.0.1-SNAPSHOT.jar --assistia.bootstrap-admin
```

Le mode n'expose pas de port HTTP et demande interactivement l'adresse, le nom et deux saisies du mot de passe
(12 caractères minimum); le mot de passe n'est ni un argument de processus ni une valeur codée en dur. Il refuse
de créer un second administrateur. Le compte `system@assistia.local` préservé par V2 reste un compte technique
sans hash et ne peut pas se connecter, malgré sa valeur historique `ADMIN`.

La migration V3 ajoute `password_hash` nullable afin de préserver les comptes déjà présents sans mot de passe.
Flyway l'applique normalement au démarrage; V1 et V2 ne doivent pas être modifiées ni rejouées manuellement.
Les comptes existants sans hash ne peuvent pas se connecter. L'API admin ne remplace pas encore les mots de passe
d'un compte existant; un flux de réinitialisation devra être ajouté si cela devient nécessaire.

## Autorisations des tickets

Tous les endpoints métier requièrent une session authentifiée. Les contrôles sont faits côté serveur; envoyer un
`creatorId` ou un `role` dans le JSON ne modifie jamais l'identité ni les droits du principal.

| Rôle | Permissions |
| --- | --- |
| `USER` | Liste et consulte uniquement ses tickets; crée pour son propre compte; modifie le titre et la description de ses tickets. Le statut et la priorité à la création sont fixés par le serveur (`OPEN`, `MEDIUM`); la catégorie, le statut, la priorité et l'assignation ne sont pas modifiables par ce rôle. Une ressource appartenant à un autre utilisateur répond `404`. |
| `TECHNICIAN` | Consulte et modifie tous les tickets; change leur statut/priorité et assigne des techniciens existants. |
| `ADMIN` | Dispose des permissions ticket de gestion et peut provisionner des utilisateurs/techniciens. La création d'un autre administrateur reste réservée au bootstrap explicite. |

Les endpoints `PATCH /api/tickets/{id}/status`, `PATCH /api/tickets/{id}/priority` et
`PATCH /api/tickets/{id}/assignment` exigent `TECHNICIAN` ou `ADMIN`. La liste filtre toujours par propriétaire
pour un rôle `USER`, même lorsque les filtres `status` et `priority` sont employés.

## API tickets

| Méthode | Chemin | Opération |
| --- | --- | --- |
| `POST` | `/api/tickets` | Créer un ticket (`201 Created`, en-tête `Location`) |
| `GET` | `/api/tickets` | Lister; filtres facultatifs `status` et `priority` |
| `GET` | `/api/tickets/{id}` | Récupérer un ticket |
| `PUT` | `/api/tickets/{id}` | Modifier les champs autorisés selon le rôle |
| `PATCH` | `/api/tickets/{id}/status` | Modifier le statut (`TECHNICIAN`/`ADMIN`) |
| `PATCH` | `/api/tickets/{id}/priority` | Modifier la priorité (`TECHNICIAN`/`ADMIN`) |
| `PATCH` | `/api/tickets/{id}/assignment` | Assigner un technicien (`TECHNICIAN`/`ADMIN`) |

Les requêtes et réponses utilisent des DTO et les valeurs d'enum sont les noms Java (par exemple `IN_PROGRESS`,
`HIGH`, `VPN`). Les erreurs d'authentification sont `401`, les droits insuffisants `403`, les données invalides
`400`, et une ressource inexistante ou non visible `404`; les réponses d'erreur ne contiennent ni stack trace ni
détail SQL.
