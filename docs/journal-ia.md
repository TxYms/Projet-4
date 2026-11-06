# Journal de l'utilisation de l'IA

Ce journal suit le modèle proposé par OpenClassrooms : pour chaque étape, ce que j'ai demandé, ce que l'IA a produit, ce que j'en ai fait et comment je l'ai vérifié.

- **Outil utilisé** : Claude (Anthropic), en mode agent : il lit les fichiers du projet, écrit du code, lance les commandes (build, tests, serveur) et me rend compte.
- **Rôle de l'IA** : copilote de développement. Elle propose et écrit ; je décide, je relis et je valide.
- **Rôle que je garde** : choix du périmètre et de la stack, arbitrages entre spécifications et maquettes, relecture de chaque fichier avant fusion, capacité à expliquer et modifier chaque partie du code en soutenance.

Les commits produits avec l'IA portent la mention `Co-Authored-By: Claude`.

> Les colonnes « Ce que j'en ai fait » marquées **[à compléter]** sont à remplir pendant ma relecture : ce que j'ai modifié, refusé ou compris différemment.

---

## Étape 0 : lecture du sujet et cadrage

| | |
|---|---|
| **Demande** | Lire la page du projet, les spécifications (PDF) et les maquettes Figma, et me faire une synthèse : périmètre, livrables, points de vigilance. |
| **Production de l'IA** | Synthèse des US01 à US10, liste des livrables, déroulé de la soutenance, points d'attention (taille de 1 Go, journal de l'IA, charge de travail). Liste des écrans des maquettes et relevé des couleurs. |
| **Mes décisions** | Stack **Spring Boot + Angular + PostgreSQL + stockage local** (je connais déjà Spring Boot et Angular grâce au projet précédent). Périmètre **MVP seul (US01 à US06)**. Méthode : une première version complète par l'IA, puis relecture complète par moi. |
| **Vérification** | Relecture du sujet et des spécifications en parallèle de la synthèse. |

## Étape 1 : architecture et modèle de données

| | |
|---|---|
| **Demande** | Proposer une architecture et un modèle de données pour le MVP. |
| **Production de l'IA** | Découpage front / API / PostgreSQL / disque, deux tables (`users`, `stored_files`), migration Flyway, contrat des endpoints. Organisation du code par fonctionnalité (`auth`, `file`, `download`…). |
| **Arbitrages relevés par l'IA** | 1) Les spécifications disent que les métadonnées sont supprimées à l'expiration, mais les maquettes montrent un onglet « Expiré ». Proposition : contenu supprimé la nuit suivante, métadonnées gardées 30 jours. 2) Les tags (US08) sont cités dans US01 mais absents des maquettes : laissés hors MVP. |
| **Ce que j'en ai fait** | **[à compléter]** : valider ou modifier ces deux arbitrages, et savoir les justifier devant Lisa. |
| **Vérification** | L'API démarre sur PostgreSQL et Flyway crée le schéma ; Hibernate valide que les entités correspondent aux tables (`ddl-auto: validate`). |

## Étape 2 : API Spring Boot

| | |
|---|---|
| **Demande** | Implémenter US01 à US06 côté serveur, avec des tests. |
| **Production de l'IA** | Contrôleurs, services, sécurité JWT, stockage, purge planifiée, gestion des erreurs, 34 tests. |
| **Erreurs trouvées par les tests** | Deux bugs de la première version, détectés par les tests d'intégration et corrigés : <br>1. **Mauvaise injection de dépendance** : le service de téléchargement recevait le décodeur des jetons de connexion au lieu de celui des tickets (deux beans du même type, Spring choisissait celui marqué `@Primary`). Résultat : aucun fichier protégé ne pouvait être téléchargé, même avec le bon mot de passe. Corrigé avec `@Qualifier`, et un test vérifie aussi qu'un jeton de connexion ne peut pas servir de ticket. <br>2. La vérification de l'expiration des JWT utilisait l'horloge système au lieu de l'horloge de l'application : incohérent, et impossible à tester. Corrigé en injectant la même `Clock` partout. |
| **Ce que j'en ai fait** | **[à compléter]** : relecture de `SecurityConfig`, `JwtService`, `FileService`, `DownloadService`. |
| **Vérification** | `mvn verify` : 34 tests verts, 93 % de couverture. Test manuel d'un fichier de 1 Go (accepté), de 1,07 Go (refusé), mesure de la mémoire. |

## Étape 3 : front Angular

| | |
|---|---|
| **Demande** | Reproduire les maquettes Figma : accueil, connexion, inscription, téléversement, téléchargement, mon espace, en version ordinateur et mobile. |
| **Production de l'IA** | Pages, services, intercepteur, gardes, 37 tests unitaires, captures d'écran pour comparer aux maquettes. |
| **Écart volontaire avec les maquettes** | L'orange des boutons a été assombri (`#e9844a` → `#b4511b`) : la couleur d'origine n'atteignait pas le contraste minimal de 4,5:1 exigé pour l'accessibilité (WCAG AA). La durée d'expiration par défaut est « Une semaine » (spécifications) et non « Une journée » (maquette). |
| **Ce que j'en ai fait** | **[à compléter]** : comparer chaque écran aux maquettes. |
| **Vérification** | `npm run test:ci` : 37 tests, 93 % de couverture. Lighthouse : accessibilité 100, performance 97. |

## Étape 4 : tests de bout en bout, performance, sécurité

| | |
|---|---|
| **Demande** | Scénarios de bout en bout, test de charge, scan de sécurité. |
| **Production de l'IA** | 5 scénarios Playwright (ordinateur + mobile), script de charge, configuration OWASP Dependency-Check, CI GitHub Actions, Dependabot. |
| **Choix d'outils** | **Playwright au lieu de Cypress** et **autocannon au lieu de k6** : l'environnement de l'IA ne pouvait pas télécharger les binaires de Cypress et de k6. Les deux remplaçants s'installent avec npm et utilisent le Chrome du poste. Les spécifications autorisent « Cypress ou équivalent » et « k6 ou autre outil ». |
| **Résultat notable** | Le test de charge montre que la connexion plafonne à 23 requêtes/s à cause de BCrypt. C'est voulu (sécurité), mais cela fait de la limitation des tentatives la première évolution à prévoir. |
| **Ce que j'en ai fait** | **[à compléter]** |
| **Vérification** | 10 exécutions Playwright vertes. `npm audit` : 0 vulnérabilité. Le scan OWASP n'a pas pu tourner dans l'environnement de l'IA (base NVD inaccessible) : il est prévu dans la CI hebdomadaire. **[à faire : le lancer une fois sur mon poste ou dans GitHub Actions]** |

## Étape 5 : documentation

| | |
|---|---|
| **Demande** | README, TESTING.md, SECURITY.md, PERF.md, MAINTENANCE.md, documentation d'architecture. |
| **Production de l'IA** | Les fichiers du dépôt, rédigés à partir du code et des mesures réelles. |
| **Ce que j'en ai fait** | **[à compléter]** |
| **Vérification** | Chaque commande du README a été exécutée ; chaque chiffre vient d'une mesure faite le 6 novembre 2026. |

---

## Bilan : ce que l'IA a bien fait, ce qu'il faut surveiller

**[à compléter par moi après la relecture]** Pistes :

- Gain de temps sur le code « attendu » (configuration Spring, formulaires, tests).
- Les tests ont attrapé deux erreurs que l’IA avait elle-même introduites : sans eux, le téléchargement des fichiers protégés aurait été cassé. **Écrire les tests en même temps que le code** est la règle que je retiens.
- Points où mon jugement a été nécessaire : arbitrages entre maquettes et spécifications, périmètre, priorités de sécurité.
