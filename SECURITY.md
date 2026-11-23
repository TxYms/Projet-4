# Sécurité

## 1. Mesures en place

### Authentification et comptes (US03, US04)

| Risque | Mesure | Où |
|---|---|---|
| Vol de la base de données | Mots de passe hachés avec BCrypt (salé, coût 10). Le hachage n'est jamais renvoyé par l'API. | `SecurityConfig.passwordEncoder`, `AuthControllerIT` |
| Découverte des comptes existants | Même message « E-mail ou mot de passe incorrect » que l'e-mail existe ou non | `AuthService.login` |
| Doublons d'e-mail (`Alice@…` / `alice@…`) | E-mail normalisé en minuscules + contrainte d'unicité en base | `AuthService.normalize`, `V1__init_schema.sql` |
| Session volée ou rejouée | Jeton JWT signé HS256, valable 1 heure, vérifié à chaque requête (signature, émetteur, dates, type) | `SecurityConfig`, `JwtService` |
| Attaque CSRF | API sans cookie ni session : le jeton est envoyé explicitement dans l'en-tête `Authorization`, un site tiers ne peut pas l'ajouter | `SecurityConfig` (CSRF désactivé, `STATELESS`) |
| Secret de signature faible | L'API refuse de démarrer avec un secret de moins de 256 bits | `JwtKeys` |

### Fichiers et liens (US01, US02, US06)

| Risque | Mesure | Où |
|---|---|---|
| Lien deviné ou énuméré | Lien de 128 bits issus de `SecureRandom` (22 caractères) : impossible à deviner | `TokenGenerator` |
| Accès au fichier d'un autre utilisateur | Chaque requête vérifie le propriétaire ; un fichier qui n'est pas à soi répond 404 (on ne révèle pas son existence) | `FileService.delete`, `FileFlowIT.user_cannot_delete_someone_else_file` |
| Écriture hors du dossier de stockage (`../../etc/passwd`) | Le fichier est enregistré sous un identifiant généré, jamais sous son nom ; les clés de stockage sont validées par une expression régulière | `LocalStorageService.resolve`, `LocalStorageServiceTest` |
| Fichier exécutable malveillant | Extensions interdites (exe, bat, cmd, com, msi, scr, ps1, vbs, jar, sh, dll), contrôlées côté front **et** côté serveur | `FileValidator`, `application.yml` |
| Contenu interprété par le navigateur (XSS via un fichier HTML ou SVG) | Toujours servi en `application/octet-stream`, en pièce jointe, avec `X-Content-Type-Options: nosniff` | `DownloadController.content` |
| Mot de passe d'un fichier dans l'URL | Le mot de passe est envoyé en POST ; l'API répond un ticket JWT de 5 minutes, valable pour ce seul fichier, qui sert à lancer le téléchargement | `DownloadService.unlock`, `DownloadFlowIT.ticket_is_bound_to_one_file` |
| Confusion entre jeton de connexion et ticket | Revendication `typ` (`access` / `download`) vérifiée par deux décodeurs distincts | `SecurityConfig.decoderFor`, `DownloadFlowIT.access_token_cannot_be_used_as_download_ticket` |
| Saturation du disque ou de la mémoire | Taille limitée à 1 Go par Spring et par le validateur ; écriture en flux, sans chargement en mémoire | `application.yml`, `PERF.md` |
| Fichier conservé au-delà de la durée annoncée | Lien refusé dès l'échéance, contenu supprimé par la purge nocturne | `DownloadService.findAvailable`, `ExpiredFilesPurgeJob` |

### Validation et erreurs

- Toutes les entrées sont validées **côté serveur** (Bean Validation + `FileValidator`), même quand le front les contrôle déjà : les contrôles du front servent au confort, pas à la sécurité.
- Les erreurs sont renvoyées au format Problem Details, sans trace d'exécution, requête SQL ni détail interne (`GlobalExceptionHandler`, `server.error.include-stacktrace: never`). Les erreurs inattendues sont journalisées côté serveur uniquement.

### En-têtes HTTP de l'API

Vérifiés avec `curl -I` :

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
Referrer-Policy: no-referrer
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
```

`Referrer-Policy: no-referrer` évite que le lien de téléchargement fuite vers un site tiers via l'en-tête `Referer`.

### Front

- Le jeton est gardé dans le `sessionStorage` : il disparaît à la fermeture de l'onglet. Angular échappe tout contenu affiché (noms de fichiers compris), ce qui limite le risque XSS qui pourrait le lire.
- L'intercepteur n'ajoute le jeton qu'aux appels de l'API, jamais aux routes publiques.
- La police est servie localement : aucune donnée de visiteur n'est envoyée à un service tiers.

### CORS

En développement, le front passe par le proxy d'Angular : aucune requête cross-origin. En production, seules les origines listées dans `CORS_ORIGINS` peuvent appeler l'API, avec les seuls en-têtes `Authorization` et `Content-Type`.

## 2. Scan des dépendances

### Front (npm audit)

```bash
cd frontend && npm audit
```

Résultat du 6 novembre 2026 :

```
found 0 vulnerabilities
```

Les dépendances de production comme de développement sont saines.

### API (OWASP Dependency-Check)

Le plugin OWASP Dependency-Check est configuré dans le profil Maven `security`. Il compare chaque dépendance à la base de vulnérabilités NVD et **fait échouer le build si une faille de gravité 7 ou plus (CVSS) est trouvée**.

```bash
cd backend && mvn -Psecurity -DskipTests verify
# Rapport : backend/target/dependency-check-report.html
```

Il a besoin d'accéder à la base NVD (une clé d'API gratuite accélère le premier téléchargement : variable `NVD_API_KEY`). Il tourne automatiquement **chaque lundi** dans GitHub Actions (`.github/workflows/security.yml`), où le rapport est conservé comme artefact.

Vérification complémentaire faite le 6 novembre 2026 avec `mvn versions:display-dependency-updates` : toutes les dépendances de l'API sont à leur dernière version publiée (Spring Boot 4.1.2, springdoc 3.2.0). Spring Boot fixe les versions de ses dépendances (Hibernate, Tomcat, Jackson, driver PostgreSQL…), ce qui les met à jour ensemble.

### Surveillance continue

Dependabot (`.github/dependabot.yml`) ouvre chaque semaine une pull request quand une dépendance Maven ou npm a une nouvelle version, et immédiatement quand une faille est publiée. La CI rejoue tous les tests sur cette pull request avant fusion.

## 3. Décisions et limites connues

| Sujet | Décision pour le prototype | Évolution prévue |
|---|---|---|
| Force brute sur la connexion et sur les mots de passe de fichiers | Pas de limitation du nombre d'essais. BCrypt ralentit chaque essai (environ 80 ms par cœur sur la machine de test, voir PERF.md). | Limiter les tentatives par IP et par lien (Bucket4j, ou au niveau du serveur web). Priorité n° 1. |
| Révocation des jetons | Un jeton reste valable jusqu'à son expiration (1 heure), même après déconnexion. | Durée plus courte + jeton de rafraîchissement, ou liste de révocation. |
| Analyse antivirus | Seules les extensions sont filtrées ; un fichier renommé passe. | Analyse ClamAV des fichiers avant de délivrer le lien. |
| Chiffrement au repos | Les fichiers sont stockés en clair sur le disque du serveur. | Chiffrement du volume, ou stockage S3 avec chiffrement côté serveur. |
| Ticket dans l'URL du téléchargement | Le ticket apparaît dans les journaux d'accès. Il ne vaut que 5 minutes et pour un seul fichier, et ne révèle pas le mot de passe. | Acceptable ; on peut masquer le paramètre dans les journaux. |
| HTTPS | Non géré par l'API. | Terminaison TLS par le serveur web ou l'hébergeur, avec HSTS. |
| Conservation de l'historique | Les métadonnées d'un fichier expiré restent 30 jours (onglet « Expiré » des maquettes), son contenu est supprimé la nuit suivant l'expiration. | Durée réglable (`datashare.purge.metadata-retention`), à valider avec le produit et au regard du RGPD. |
| Documentation Swagger | Accessible sans authentification. | La désactiver en production (`springdoc.api-docs.enabled=false`). |

## 4. Signaler une vulnérabilité

Prototype non déployé publiquement. Pour toute question de sécurité, ouvrir une issue privée (« Security advisory ») sur le dépôt GitHub.
