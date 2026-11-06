# Suivi de performance

## Environnement de mesure

Toutes les mesures ont été faites le 6 novembre 2026 sur la même machine, API et outil de charge côte à côte :

- 2 vCPU, Linux, Java 21 (OpenJDK), PostgreSQL 16 local, stockage sur disque local ;
- API lancée avec `java -jar` sans réglage particulier de la JVM ;
- les chiffres sont donc à lire comme des ordres de grandeur et des points de comparaison, pas comme la capacité d'un serveur de production.

## 1. Test de charge des endpoints

### Outil et script

Le test utilise [autocannon](https://github.com/mcollina/autocannon), un outil de charge HTTP en Node.js, équivalent à k6 pour ce besoin. Il a été choisi parce qu'il s'installe avec npm, comme le reste du front, sans binaire supplémentaire.

Le script `perf/load-test.mjs` :

1. crée un compte, se connecte et téléverse un fichier de 1 Mo ;
2. envoie pendant 15 secondes, avec 20 connexions simultanées, des requêtes sur quatre scénarios.

```bash
cd perf
npm install
node load-test.mjs 15    # durée en secondes
```

### Résultats

| Scénario | Requêtes/s | Latence p50 | Latence p97,5 | Latence p99 | Erreurs |
|---|---|---|---|---|---|
| Métadonnées d'un lien (`GET /api/download/{token}`) | 958 | 69 ms | 140 ms | 155 ms | 0 |
| Téléchargement d'un fichier de 1 Mo (`GET …/content`) | 500 | 91 ms | 202 ms | 230 ms | 0 |
| Historique connecté (`GET /api/files`) | 1 037 | 79 ms | 176 ms | 193 ms | 0 |
| Connexion (`POST /api/auth/login`) | 23 | 816 ms | 1 777 ms | 2 114 ms | 0 |

Le téléchargement de 1 Mo atteint environ **500 Mo/s** de débit.

### Interprétation

- **Aucune erreur** sur 15 secondes et 20 connexions simultanées, quel que soit le scénario.
- **Lecture et téléchargement** : la page de téléchargement, l'endpoint le plus appelé (chaque destinataire l'ouvre), tient près de 1 000 requêtes par seconde sur 2 vCPU. Le flux de fichier est copié du disque vers le réseau sans passer en mémoire, d'où un débit élevé.
- **Connexion lente, et c'est voulu** : 23 connexions par seconde seulement. Chaque connexion calcule un hachage BCrypt (coût 10), conçu pour être lent afin de rendre coûteuses les attaques par force brute. C'est le principal point de vigilance :
  - c'est un compromis assumé entre sécurité et capacité, qui ne gêne pas un usage normal (un utilisateur se connecte une fois par heure au plus) ;
  - en revanche, une rafale de tentatives peut saturer le processeur : la limitation du nombre de tentatives par adresse IP est la première évolution à prévoir (voir [SECURITY.md](SECURITY.md)) ;
  - si le trafic augmente, il suffit d'ajouter des instances de l'API : elle est sans état (pas de session), donc elle se duplique derrière un répartiteur de charge sans configuration supplémentaire.

## 2. Fichiers volumineux (1 Go)

La règle « taille maximale 1 Go » est le cas le plus exigeant du produit. Mesures avec `curl` :

| Mesure | Résultat |
|---|---|
| Téléversement d'un fichier de 1 Go | 8,4 s, réponse 201 |
| Téléchargement du même fichier | 1,2 s, 1 073 741 824 octets reçus |
| Téléversement d'un fichier de 1,07 Go | refusé, réponse 413 « Le fichier dépasse la taille maximale de 1 Go. » |
| Mémoire de l'API (RSS) avant / après le téléversement de 1 Go | 498 Mo / 498 Mo |

La mémoire ne bouge pas : le fichier n'est jamais chargé en entier. Trois réglages le garantissent :

- `spring.servlet.multipart.file-size-threshold: 0` : Tomcat écrit le fichier sur disque dès réception ;
- `LocalStorageService` copie ensuite un flux (`Files.copy`) vers le dossier de stockage ;
- le téléversement est fait **hors transaction** (`FileService.upload`) : une copie longue ne bloque pas une connexion à la base.

Côté navigateur, le front envoie le fichier avec `FormData` et suit la progression ; au téléchargement, il laisse le navigateur récupérer le flux directement (lien `<a>`) au lieu de le charger en JavaScript.

## 3. Budget de performance du front

### Taille du bundle

Mesurée avec `npm run build` (configuration de production) :

| Élément | Taille brute | Transférée (compressée) | Budget |
|---|---|---|---|
| Chargement initial (JS + CSS) | 270 Ko | **74 Ko** | alerte à 500 Ko, erreur à 1 Mo |
| Page d'accueil / téléversement | 11 Ko | 3,9 Ko | chargée à la demande |
| Mon espace | 12 Ko | 3,6 Ko | chargée à la demande |
| Page de téléchargement | 5 Ko | 2,0 Ko | chargée à la demande |

Les budgets sont configurés dans `frontend/angular.json` : le build échoue si le chargement initial dépasse 1 Mo. Chaque page est chargée à la demande : un destinataire qui ouvre un lien ne charge pas le code de l'espace personnel.

La police DM Sans est servie par l'application elle-même (paquet `@fontsource-variable/dm-sans`) plutôt que par Google Fonts : une requête vers un domaine tiers en moins, et aucune adresse IP de visiteur transmise à Google.

### Lighthouse

Page de connexion, build de production, Lighthouse en mode navigation (profil mobile simulé) :

| Indicateur | Résultat |
|---|---|
| Score Performance | **97** |
| Score Accessibilité | **100** |
| Score Bonnes pratiques | **100** |
| First Contentful Paint | 0,6 s |
| Largest Contentful Paint | 0,6 s |
| Total Blocking Time | 200 ms |
| Cumulative Layout Shift | 0 |

## 4. Métriques suivies en fonctionnement

| Métrique | Où la lire |
|---|---|
| Temps de réponse et codes HTTP | Journaux d'accès du serveur web, ou `management.endpoints` d'Actuator à activer (`metrics`) |
| Taille des fichiers téléversés | Journal applicatif : chaque téléversement écrit sa taille et sa date d'expiration |
| Résultat de la purge nocturne | Journal applicatif : nombre de contenus et d'entrées supprimés |
| État de l'API | `GET /actuator/health` |

Exemple de lignes de journal :

```
INFO  fr.datashare.file.FileService : Fichier 6b90755e-… téléversé par 81472c2a-… (300000000 octets, expire le 2026-11-08T13:30:08Z)
INFO  fr.datashare.purge.ExpiredFilesPurgeJob : Purge des fichiers expirés : 3 contenus supprimés, 1 entrées d'historique supprimées
```

## 5. Pistes d'optimisation

Par ordre de priorité :

1. **Limiter les tentatives de connexion** (par IP et par compte) pour protéger le processeur des rafales BCrypt.
2. **Stockage objet (S3)** avec URL pré-signées : le navigateur enverrait et récupérerait les fichiers directement auprès du stockage, sans passer par l'API. C'est le principal levier si les volumes augmentent. L'interface `StorageService` est prévue pour ce remplacement.
3. **Pagination de l'historique** : inutile pour un prototype, mais la liste est aujourd'hui renvoyée en entier.
4. **Exposer les métriques** (Actuator + Prometheus) pour suivre ces chiffres dans le temps au lieu de les mesurer ponctuellement.
