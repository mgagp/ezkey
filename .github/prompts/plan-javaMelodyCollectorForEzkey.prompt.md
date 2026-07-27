## Plan: JavaMelody Collector for Ezkey

Évaluer puis intégrer JavaMelody de façon légère sur les back-ends servlet d’ezkey, avec un Collector central dans le stack Docker de base. L’approche recommandée est d’utiliser l’intégration officielle Spring Boot 4 pour admin-api, auth-api et integration-api uniquement, d’exposer les rapports via les ports management déjà présents, et de lancer un service Collector Docker dédié en port local direct. Cela minimise les changements de sécurité sur les ports métiers, évite d’ajouter du proxy Caddy pour ce sujet, et reste cohérent avec le clean start par défaut.

**Steps**
1. Phase 1 — Spike de compatibilité documentaire. Confirmer le couple exact d’artefacts JavaMelody à utiliser avant tout codage: `javamelody-spring-boot4-starter` pour les APIs Spring Boot 4, et la version précise de `javamelody-collector-server.war` compatible avec des applications monitorées en 2.x Jakarta. Cette étape bloque toutes les autres parce que la doc officielle mentionne explicitement une nuance de compatibilité du Collector.
2. Phase 1 — Vérifier le mode de déploiement du Collector retenu. Recommandation: ne pas embarquer le Collector dans un autre serveur Java déjà présent; créer un service Docker dédié qui lance le WAR officiel en mode standalone (`java -jar javamelody-collector-server.war`). Cela reste conforme à la documentation officielle et évite d’introduire Tomcat/Caddy additionnels pour ce seul besoin.
3. Phase 2 — Ajouter JavaMelody aux modules ciblés, en parallèle sur admin-api, auth-api et integration-api. Ajouter la dépendance officielle Spring Boot 4, configurer `javamelody.enabled=true`, `javamelody.management-endpoint-monitoring-enabled=true`, et les `init-parameters` minimaux utiles en dev/ops (par exemple `update-check-disabled`, éventuellement `http-transform-pattern`, `sql-transform-pattern`, `spring-monitoring-enabled`). Exclure explicitement crypto-api du périmètre.
4. Phase 2 — Aligner l’exposition des endpoints management. Ajouter `monitoring` aux endpoints exposés sur les ports management existants sans ouvrir le monitoring sur les ports applicatifs. Pour admin-api et auth-api, étendre l’exposition actuelle. Pour integration-api, adapter la configuration dans le dossier `config/` versionné, qui est le vrai point de configuration de ce module en Docker.
5. Phase 2 — Ajouter la mécanique d’auto-enregistrement auprès du Collector. Implémenter, dans chaque API concernée, un hook de cycle de vie au démarrage/arrêt qui appelle l’API officielle JavaMelody d’enregistrement/désenregistrement de nœud. Comme le monitoring sera servi via les ports management, l’URL de nœud fournie au Collector devra être la base management incluant `/actuator`, afin que le Collector résolve ensuite `/actuator/monitoring` correctement.
6. Phase 2 — Standardiser le nommage des applications dans le Collector. Utiliser des noms stables et explicites (`admin-api`, `auth-api`, `integration-api`) pour éviter des noms dérivés de hostname/contexte qui changeraient selon les environnements. Prévoir des variables d’environnement Docker pour l’URL du Collector et, si nécessaire, pour le nom déclaré.
7. Phase 3 — Ajouter le service Collector au stack Docker par défaut. Recommandation: créer un service dédié dans le compose de base ou un override automatiquement inclus par clean-start, puisque la décision retenue est “activé par défaut”. Exposer le Collector sur un port direct local uniquement, non proxifié par Caddy. Prévoir un volume persistant pour le stockage JavaMelody afin de conserver les séries et l’état applicatif du Collector.
8. Phase 3 — Injecter la configuration de registration dans les services Docker concernés. Ajouter dans `docker-compose.yml` les variables nécessaires pour admin-api, auth-api et integration-api. Utiliser les noms de service Docker internes (`admin-api`, `auth-api`, `integration-api`, `javamelody-collector`) et les ports management internes pour que l’enregistrement fonctionne dès le clean start, sans configuration manuelle côté développeur.
9. Phase 3 — Décider du niveau de sécurité minimal du monitoring local. Recommandation initiale: environnement Docker local seulement, exposition directe sur localhost du Collector, et endpoints JavaMelody des APIs accessibles via les ports management du réseau Docker/host de dev. Ne pas ajouter de proxy Caddy ni de durcissement excessif pour cette première itération, mais documenter clairement que cette posture est réservée au développement et aux environnements internes.
10. Phase 4 — Documenter la DX et les limites. Ajouter une documentation concise expliquant: composants concernés, URL du Collector, fonctionnement de l’auto-enregistrement, exclusion de crypto-api, où consulter les métriques SQL/mémoire, et les limites connues (outil léger, pas de centralisation globale de type Prometheus/Grafana, pas pensé pour l’observabilité long terme).
11. Phase 4 — Prévoir les validations ciblées. Vérifier qu’un clean start sans paramètre fait apparaître immédiatement les trois APIs dans le Collector, que les données se rafraîchissent, que les stats SQL remontent après trafic réel, que l’arrêt/redémarrage d’un service met à jour l’état du nœud, et que crypto-api n’embarque aucune dépendance JavaMelody. Ajouter au minimum une vérification documentaire/manuelle du workflow Docker; ajouter des tests automatisés uniquement si le code d’auto-enregistrement introduit une logique isolable utile à tester.

**Relevant files**
- `c:\github\ezkey-worktree2\docker\docker-compose.yml` — point principal pour injecter le service Collector et les variables d’environnement des APIs.
- `c:\github\ezkey-worktree2\docker\start.sh` — logique d’assemblage du stack; utile si l’intégration du Collector doit suivre le pattern des overrides optionnels.
- `c:\github\ezkey-worktree2\ezkey-tests\clean-start.sh` — point d’entrée du clean start par défaut; à garder cohérent avec l’activation par défaut du monitoring.
- `c:\github\ezkey-worktree2\ezkey-admin-api\pom.xml` — ajout de la dépendance JavaMelody Spring Boot 4.
- `c:\github\ezkey-worktree2\ezkey-auth-api\pom.xml` — ajout de la dépendance JavaMelody Spring Boot 4.
- `c:\github\ezkey-worktree2\ezkey-integration-api\pom.xml` — ajout de la dépendance JavaMelody Spring Boot 4.
- `c:\github\ezkey-worktree2\ezkey-integration-api\config\application-docker.properties` — vrai point de config Docker de integration-api; y ajouter l’exposition `monitoring` et les propriétés JavaMelody.
- `c:\github\ezkey-worktree2\ezkey-admin-api\src\main\java\org\ezkey\admin\config\SecurityConfig.java` — référence de sécurité montrant pourquoi le mode management endpoint évite d’ouvrir `/monitoring` sur le port métier.
- `c:\github\ezkey-worktree2\ezkey-auth-api\src\main\java\org\ezkey\auth\config\SecurityConfig.java` — référence du modèle de sécurité permissif auth-api.
- `c:\github\ezkey-worktree2\ezkey-integration-api\src\main\java\org\ezkey\integration\api\config\IntegrationApiSecurityConfig.java` — référence de sécurité montrant le même intérêt à rester sur le port management.
- `c:\github\ezkey-worktree2\docker\docker-compose.with-proxy.yml` — fichier explicitement hors périmètre initial, pour garder le Collector en port direct local seulement.

**Verification**
1. Vérifier la compatibilité exacte des artefacts JavaMelody retenus à partir de la documentation officielle et des releases avant d’implémenter le Collector Docker.
2. Lancer un clean start standard et confirmer que le service Collector démarre sans paramètre additionnel.
3. Ouvrir l’UI du Collector et vérifier la présence automatique de `admin-api`, `auth-api` et `integration-api` sans ajout manuel.
4. Générer un peu de trafic réel sur les trois APIs et vérifier l’apparition des compteurs HTTP, SQL, mémoire JVM et threads dans le Collector.
5. Vérifier que l’URL directe de monitoring de chaque service est bien portée par le port management attendu et non par le port métier.
6. Redémarrer un des services monitorés et vérifier que son nœud se ré-enregistre et que le Collector reflète correctement son indisponibilité puis son retour.
7. Vérifier qu’aucune dépendance/config JavaMelody n’a été ajoutée à crypto-api.
8. Exécuter la baseline Maven/formatting du repo si des modules Java sont modifiés, puis un démarrage Docker ciblé du stack concerné.

**Decisions**
- Inclus: admin-api, auth-api, integration-api, Collector Docker, auto-enregistrement, clean start par défaut.
- Exclu: crypto-api, exposition via Caddy, stack Prometheus/Grafana, durcissement production complet à cette étape.
- Recommandation technique: servir JavaMelody via les ports management existants (`/actuator/monitoring`) plutôt que via `/monitoring` sur les ports applicatifs.
- Recommandation de déploiement: Collector standalone officiel dans un conteneur dédié avec volume persistant.
- Point de vigilance majeur: la doc officielle indique une nuance de version pour `javamelody-collector-server.war`; cette confirmation doit précéder toute implémentation.

**Further Considerations**
1. Paramètres JavaMelody à activer au départ: rester minimal (`update-check-disabled`, éventuellement `http-transform-pattern`/`sql-transform-pattern`) puis enrichir après observation, plutôt que d’activer d’emblée les options avancées.
2. Sécurité locale: si le stack Docker local commence à être partagé à distance, ajouter ensuite une auth basique ou une restriction d’accès IP sur le Collector avant toute utilisation élargie.
3. Persistance: décider si le volume du Collector doit survivre aux `clean-start` destructifs ou repartir vide à chaque reset selon l’objectif principal (diagnostic ponctuel vs historique court).
