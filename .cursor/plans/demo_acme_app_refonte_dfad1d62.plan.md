---
name: Demo ACME App Refonte
overview: Refonte complète de ezkey-demo-app-acme pour en faire une application de démonstration du login EZKey avec une approche frontend-first (SPA-like), intégrée au stack Docker, tout en conservant le style Neo Brutalism.
todos:
  - id: cleanup
    content: Supprimer les controllers, services et templates obsoletes (integrations, enrollments, auth-attempts)
    status: completed
  - id: config
    content: Modifier application.properties avec configuration externalisee (URLs, login mode)
    status: completed
  - id: login-page
    content: Creer la page login.html avec formulaire username et checkbox challenge
    status: completed
  - id: login-js
    content: Creer ezkey-login.js pour les appels API frontend (login + polling wait)
    status: completed
  - id: dashboard
    content: Creer dashboard.html avec widget explicatif des 2 modes d'integration
    status: completed
  - id: controller
    content: Modifier HomeController pour gerer login/dashboard/logout routes
    status: completed
  - id: layout
    content: Simplifier base.html avec navigation minimaliste
    status: completed
  - id: docker
    content: Ajouter demo-app-acme au docker-compose.yml et Dockerfile
    status: completed
  - id: docs
    content: Creer README.md et AGENTS.md (extremement courts et precis)
    status: completed
---

