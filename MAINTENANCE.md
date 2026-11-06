# Maintenance

## 1. Inventaire des dépendances principales

Versions au 6 novembre 2026.

### API

| Dépendance | Version | Rôle | Fin de support connue |
|---|---|---|---|
| Java (OpenJDK) | 21 (LTS) | Langage et JVM | Support long terme jusqu'en 2029 au moins |
| Spring Boot | 4.1.2 | Socle de l'API, fixe les versions de la plupart des bibliothèques ci-dessous | Chaque version mineure est maintenue environ 13 mois en open source |
| Spring Security | 7.1.2 | Authentification JWT | Suit Spring Boot |
| Hibernate ORM | 7.4 | Accès à la base | Suit Spring Boot |
| Tomcat (embarqué) | 11.0 | Serveur HTTP | Suit Spring Boot |
| Flyway | 12.4 | Migrations du schéma | Suit Spring Boot |
| Driver PostgreSQL | 42.7 | Connexion à la base | Suit Spring Boot |
| springdoc-openapi | 3.2.0 | Documentation Swagger | Version gérée à la main dans `pom.xml` |
| PostgreSQL (serveur) | 16 | Base de données | Support jusqu'en novembre 2028 |

### Front

| Dépendance | Version | Rôle | Fin de support connue |
|---|---|---|---|
| Node.js | 22 (LTS) | Outils de build | Avril 2027 |
| Angular | 22.2 | Framework du front | Chaque version majeure est maintenue 18 mois (6 mois actifs + 12 mois LTS) |
| TypeScript | 6.0 | Langage | Imposé par Angular |
| RxJS | 7.8 | Flux asynchrones (HTTP) | Imposé par Angular |
| Vitest | 5 | Tests unitaires | Outil de développement |
| Playwright | 1.x | Tests de bout en bout | Outil de développement |
| @fontsource-variable/dm-sans | 5 | Police | Fichiers statiques, sans risque |

## 2. Procédure de mise à jour

### Fréquences

| Type de mise à jour | Fréquence | Déclencheur |
|---|---|---|
| Correctif de sécurité (CVE de gravité ≥ 7) | **Sous 48 heures** | Alerte Dependabot, ou échec du scan hebdomadaire OWASP |
| Version corrective (`x.y.Z`) de Spring Boot, Angular ou d'une bibliothèque | Chaque mois | Pull request Dependabot (hebdomadaire) |
| Version mineure (`x.Y.0`) de Spring Boot ou d'Angular | Tous les 6 mois | Calendrier de publication des projets |
| Version majeure (Spring Boot 5, Angular 23…) | Une fois par an, avant la fin de support de la version utilisée | Planifiée comme un chantier à part |
| Java, Node.js, PostgreSQL | À chaque nouvelle version LTS, dans l'année | Planifiée |

### Étapes pour une mise à jour courante

1. **Créer une branche** : `git switch -c chore/deps-2026-12`.
2. **Mettre à jour** :
   - API : modifier la version du parent dans `backend/pom.xml` (Spring Boot), puis `mvn versions:display-dependency-updates` pour voir le reste.
   - Front : `npm outdated`, puis `npx ng update @angular/core @angular/cli` pour Angular (applique aussi les migrations de code automatiques), et `npm update` pour le reste.
3. **Lire les notes de version** des dépendances majeures (Spring Boot, Spring Security, Angular), en particulier les rubriques « breaking changes » et « deprecations ».
4. **Lancer tous les tests** :
   ```bash
   cd backend && mvn verify
   cd frontend && npm run test:ci && npm run build && npm run e2e
   cd frontend && npm audit
   ```
5. **Vérifier à la main** le parcours principal : inscription, téléversement avec mot de passe, téléchargement depuis une fenêtre privée, suppression.
6. **Ouvrir une pull request** avec un commit `chore(deps): …` ; la CI rejoue les trois niveaux de tests.
7. **Fusionner** puis surveiller les journaux de l'API après déploiement.

En cas de problème après déploiement, revenir à la version précédente du jar et du front : les migrations Flyway ne s'appliquant qu'en ajout, une version précédente reste compatible tant qu'aucune colonne n'a été supprimée.

## 3. Risques par type de mise à jour

| Mise à jour | Risque | Ce qui peut casser | Comment le limiter |
|---|---|---|---|
| Correctif de sécurité | Faible | Rarement quelque chose | Appliquer vite ; les tests suffisent |
| Version corrective | Faible | Comportement corrigé dont on dépendait sans le savoir | Tests automatiques |
| Spring Boot mineure | Moyen | Propriétés de configuration renommées ou dépréciées, nouvelles valeurs par défaut | Lire les notes de version ; le module `spring-boot-properties-migrator` signale les propriétés renommées |
| Spring Security | Moyen à élevé | Configuration du filtre de sécurité, validation des JWT : une erreur peut ouvrir une route protégée | Les tests `AuthControllerIT` et `DownloadFlowIT` vérifient explicitement les refus d'accès ; ne jamais les désactiver |
| Spring Boot majeure | Élevé | Changement de version de Java, Jakarta EE, Jackson, modules renommés (exemple réel : en passant à Spring Boot 4, les starters `web` sont devenus `webmvc` et les annotations de test ont changé de paquet) | Chantier dédié, branche longue, lecture du guide de migration officiel |
| Angular majeure | Moyen | API dépréciées supprimées, changements du système de build | `ng update` migre la plupart du code ; les tests de composants vérifient le reste |
| Hibernate | Moyen | Requêtes générées, validation du schéma au démarrage (`ddl-auto: validate`) | L'API refuse de démarrer si le schéma ne correspond plus : l'erreur est visible tout de suite, pas en production |
| PostgreSQL majeure | Moyen | Fonctionnement des index, extensions | Sauvegarde, `pg_upgrade`, tests de bout en bout sur la nouvelle version |
| Driver de BDD / Flyway | Faible à moyen | Compatibilité avec la version du serveur PostgreSQL | Tests de bout en bout sur PostgreSQL réel |

## 4. Tâches récurrentes d'exploitation

| Tâche | Fréquence | Détail |
|---|---|---|
| Purge des fichiers expirés | Chaque nuit à 3 h (automatique) | `ExpiredFilesPurgeJob` ; vérifier dans les journaux qu'elle s'exécute |
| Sauvegarde de la base | Chaque jour | `pg_dump datashare` ; les fichiers eux-mêmes sont temporaires (7 jours au plus) |
| Espace disque du stockage | Chaque semaine | Prévoir au moins la somme des fichiers téléversés sur 7 jours |
| Rotation du secret JWT | Chaque année, ou immédiatement en cas de fuite | Changer `JWT_SECRET` et redémarrer : toutes les sessions sont invalidées, les utilisateurs se reconnectent |
| Revue des alertes Dependabot | Chaque semaine | Fusionner ou expliquer pourquoi on attend |

## 5. Ajouter une évolution

- **Schéma de base** : ajouter un fichier `backend/src/main/resources/db/migration/V2__description.sql`. Ne jamais modifier une migration déjà appliquée.
- **Nouvel endpoint** : il est protégé par défaut (`/api/**` exige un jeton). Toute route publique doit être ajoutée explicitement dans `SecurityConfig`.
- **Nouvelle règle de téléversement** : `FileValidator` côté serveur, `upload-rules.ts` côté front, et un test pour chacun.
- **Stockage S3** : écrire une seconde implémentation de `StorageService` ; les services métier n'ont pas à changer.
