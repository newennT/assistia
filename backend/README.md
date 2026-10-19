# API tickets

Les endpoints REST des tickets utilisent le préfixe `/api/tickets` :

| Méthode | Chemin | Opération |
| --- | --- | --- |
| `POST` | `/api/tickets` | Créer un ticket (`201 Created`, en-tête `Location`) |
| `GET` | `/api/tickets` | Lister; filtres facultatifs `status` et `priority` |
| `GET` | `/api/tickets/{id}` | Récupérer un ticket |
| `PUT` | `/api/tickets/{id}` | Modifier le titre, la description et la catégorie |
| `PATCH` | `/api/tickets/{id}/status` | Modifier le statut |
| `PATCH` | `/api/tickets/{id}/priority` | Modifier la priorité |
| `PATCH` | `/api/tickets/{id}/assignment` | Assigner un technicien |

Les corps de requête et réponses sont des DTO; les identifiants et dates de création ne sont pas modifiables.
Les valeurs d'enum attendues sont les noms Java (par exemple `IN_PROGRESS` ou `HIGH`).

## Identité du créateur temporaire

En attendant l'authentification, `POST /api/tickets` accepte `creatorId` dans son DTO et demande au service de
vérifier que cet utilisateur existe. Cette vérification de relation **n'autorise pas** l'appelant à agir au nom
de cet utilisateur : un client peut actuellement fournir l'identifiant d'un autre compte. Cette solution est
temporaire et ne doit pas être considérée comme sûre pour la production. Dès que l'authentification sera mise en
place, l'identifiant du créateur devra provenir du principal authentifié et ne plus être accepté du client.
