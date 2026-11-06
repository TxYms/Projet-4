# Architecture de DataShare

## Vue d'ensemble

DataShare est découpé en trois blocs indépendants qui communiquent uniquement par HTTP et SQL.

```mermaid
flowchart LR
    subgraph Navigateur
        SPA["Front Angular 22<br/>(SPA, composants autonomes)"]
    end

    subgraph Serveur["API Spring Boot 4.1 (Java 21)"]
        direction TB
        SEC["Spring Security<br/>JWT HS256, sans session"]
        CTRL["Contrôleurs REST<br/>/api/auth · /api/files · /api/download"]
        SVC["Services métier<br/>Auth · File · Download · Purge"]
        REPO["Repositories JPA"]
        STO["StorageService<br/>(interface)"]
        SEC --> CTRL --> SVC
        SVC --> REPO
        SVC --> STO
    end

    DB[("PostgreSQL 16<br/>users · stored_files")]
    FS[["Disque local<br/>contenus des fichiers"]]

    SPA -- "JSON / multipart<br/>Bearer JWT" --> SEC
    REPO -- "SQL (Flyway)" --> DB
    STO --> FS
```

| Bloc | Rôle | Technologies |
|---|---|---|
| Front | Écrans des maquettes, contrôles de saisie, appel de l'API | Angular 22, TypeScript strict, Reactive Forms, signals |
| API | Règles métier, sécurité, stockage | Spring Boot 4.1, Spring Security (resource server JWT), Spring Data JPA, Flyway |
| Données | Comptes et métadonnées des fichiers | PostgreSQL 16 |
| Contenus | Octets des fichiers téléversés | Système de fichiers local, derrière l'interface `StorageService` (remplaçable par S3) |

## Organisation du code de l'API

Le code est rangé par fonctionnalité plutôt que par couche technique : tout ce qui concerne le téléchargement public est dans `download/`, tout ce qui concerne l'espace personnel dans `file/`.

```
backend/src/main/java/fr/datashare/
├── auth/       US03, US04 : inscription, connexion, utilisateur courant
├── file/       US01, US05, US06 : téléversement, historique, suppression
├── download/   US02 : métadonnées, déverrouillage par mot de passe, flux de téléchargement
├── purge/      Tâche planifiée de suppression des fichiers expirés
├── storage/    Interface StorageService + implémentation disque local
├── security/   Configuration Spring Security, émission et vérification des JWT
├── user/       Entité User et son repository
├── common/     Gestion centralisée des erreurs, générateur de liens
└── config/     Propriétés applicatives, horloge, OpenAPI
```

## Organisation du front

```
frontend/src/app/
├── core/       Services HTTP, session, intercepteur JWT, gardes de routes, fonctions de formatage
├── layout/     Cadre dégradé commun (logo, bouton de compte, pied de page)
├── shared/     Composant d'icônes
└── pages/      home (téléversement), login, register, my-files, download, not-found
```

Chaque page est chargée à la demande (lazy loading) : un visiteur qui ouvre un lien de téléchargement ne télécharge pas le code de l'espace personnel.

## Modèle de données

```mermaid
erDiagram
    USERS ||--o{ STORED_FILES : "téléverse"
    USERS {
        uuid id PK
        varchar email UK "normalisé en minuscules"
        varchar password_hash "BCrypt"
        timestamptz created_at
    }
    STORED_FILES {
        uuid id PK
        uuid owner_id FK "ON DELETE CASCADE"
        varchar original_name "nom affiché, nettoyé"
        varchar content_type
        bigint size_bytes
        varchar storage_key UK "nom du fichier sur le disque"
        varchar download_token UK "lien public, 128 bits aléatoires"
        varchar password_hash "BCrypt, NULL si non protégé"
        timestamptz created_at
        timestamptz expires_at "created_at + 1 à 7 jours"
        boolean content_purged "contenu déjà supprimé par la purge"
    }
```

Index :
- `(owner_id, created_at DESC)` pour l'historique d'un utilisateur, trié du plus récent au plus ancien ;
- `expires_at` pour la tâche de purge ;
- contraintes d'unicité sur `email`, `download_token` et `storage_key`.

Le script de création est `backend/src/main/resources/db/migration/V1__init_schema.sql`. Flyway l'applique au démarrage et garde l'historique des migrations : une évolution du schéma se fait en ajoutant `V2__…sql`, jamais en modifiant `V1`.

## Principaux flux

### Téléversement (US01)

```mermaid
sequenceDiagram
    actor U as Utilisateur connecté
    participant F as Front
    participant A as API
    participant S as Stockage
    participant D as PostgreSQL
    U->>F: choisit un fichier, mot de passe, durée
    F->>F: contrôles (taille, extension, mot de passe)
    F->>A: POST /api/files (multipart + Bearer JWT)
    A->>A: mêmes contrôles côté serveur
    A->>S: copie du flux sous une clé générée
    A->>D: INSERT stored_files (lien aléatoire, expiration)
    A-->>F: 201 + lien de téléchargement
    F-->>U: « Félicitations… » + bouton Copier le lien
```

### Téléchargement d'un fichier protégé (US02)

```mermaid
sequenceDiagram
    actor R as Destinataire
    participant F as Front
    participant A as API
    R->>F: ouvre /d/{token}
    F->>A: GET /api/download/{token}
    A-->>F: nom, taille, expiration, protégé = oui
    R->>F: saisit le mot de passe
    F->>A: POST /api/download/{token}/unlock
    A->>A: BCrypt.matches()
    A-->>F: ticket JWT valable 5 min, lié à ce lien
    F->>A: GET /api/download/{token}/content?ticket=…
    A-->>R: flux binaire en pièce jointe
```

Le ticket évite deux écueils : mettre le mot de passe dans l'URL (il finirait dans les journaux et l'historique du navigateur), ou charger le fichier en JavaScript pour pouvoir l'envoyer en POST (impossible pour 1 Go). Le navigateur télécharge directement le flux.

### Expiration

1. Dès l'échéance, l'API refuse le lien (contrôle de `expires_at` à chaque requête) et répond `410 Gone`.
2. Chaque nuit, `ExpiredFilesPurgeJob` supprime le contenu des fichiers expirés du disque.
3. Les métadonnées restent visibles 30 jours dans l'onglet « Expiré » de l'historique (comme dans les maquettes), puis sont supprimées.

## Endpoints

La documentation complète est générée à partir du code : `docs/openapi.json`, et l'interface Swagger est disponible sur http://localhost:8080/api/docs/ui quand l'API tourne.

| Méthode | Chemin | Accès | Rôle | US |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | Créer un compte | US03 |
| POST | `/api/auth/login` | public | Obtenir un jeton JWT | US04 |
| GET | `/api/auth/me` | connecté | Utilisateur courant | US04 |
| POST | `/api/files` | connecté | Téléverser (multipart : `file`, `password`, `expirationDays`) | US01 |
| GET | `/api/files?status=ALL\|ACTIVE\|EXPIRED` | connecté | Historique | US05 |
| DELETE | `/api/files/{id}` | propriétaire | Supprimer définitivement | US06 |
| GET | `/api/download/{token}` | public | Métadonnées avant téléchargement | US02 |
| POST | `/api/download/{token}/unlock` | public | Vérifier le mot de passe, obtenir un ticket | US02 |
| GET | `/api/download/{token}/content` | public (+ ticket si protégé) | Flux du fichier | US02 |

Toutes les erreurs suivent le format Problem Details (RFC 9457) avec un champ `code` stable que le front peut tester :

```json
{
  "type": "https://datashare.fr/errors/file-expired",
  "title": "Gone",
  "status": 410,
  "detail": "Ce fichier n'est plus disponible en téléchargement car il a expiré.",
  "code": "FILE_EXPIRED"
}
```
