# AssistIA — frontend

Interface Vue 3 + TypeScript d’AssistIA. L’authentification utilise les sessions et cookies du backend Spring Boot. Aucun secret ou jeton n’est stocké dans le stockage web.

## Démarrage local

Prérequis : Node.js 22.18 ou supérieur (voir `package.json`).

```sh
npm install
cp .env.example .env.local
npm run dev
```

`VITE_API_URL` indique l’URL du backend vue depuis le navigateur. Sa valeur par défaut est `http://localhost:8080`. Le backend autorise par défaut l’origine frontend `http://localhost:5173` et les cookies cross-origin.

## Vérifications

```sh
npm run type-check
npm run lint
npm run test:unit -- --run
npm run build
```

## IDE

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (désactivez Vetur).

Vue Language Tools / Volar fournit le support TypeScript des fichiers `.vue` dans l’éditeur. Voir la [configuration Vite](https://vite.dev/config/).
