package fr.datashare.file;

import fr.datashare.common.ApiException;
import fr.datashare.common.TokenGenerator;
import fr.datashare.config.DatashareProperties;
import fr.datashare.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cas d'usage de l'utilisateur connecté sur ses fichiers : téléversement (US01), historique (US05)
 * et suppression (US06).
 *
 * <p>Le service coordonne trois éléments : la base (métadonnées, via {@link StoredFileRepository}),
 * le stockage des contenus ({@link StorageService}) et les règles de saisie ({@link FileValidator}).
 * Il veille à ce que les deux sources restent cohérentes : pas de métadonnées sans contenu, pas de
 * contenu orphelin sur le disque.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** Type MIME utilisé quand le navigateur n'en fournit pas, ou en fournit un anormalement long. */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final StoredFileRepository files;
    private final StorageService storage;
    private final FileValidator validator;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** Adresse publique du front, sans « / » final, utilisée pour construire les liens de téléchargement. */
    private final String publicUrl;

    public FileService(StoredFileRepository files, StorageService storage, FileValidator validator,
                       TokenGenerator tokenGenerator, PasswordEncoder passwordEncoder, Clock clock,
                       DatashareProperties properties) {
        this.files = files;
        this.storage = storage;
        this.validator = validator;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.publicUrl = properties.publicUrl().replaceAll("/+$", "");
    }

    /**
     * Téléverse un fichier et génère son lien de téléchargement (US01).
     *
     * <p>Volontairement hors transaction : la copie d'un fichier volumineux (jusqu'à 1 Go) ne doit pas
     * monopoliser une connexion à la base. Le contenu est donc écrit d'abord, puis les métadonnées sont
     * enregistrées ; si cet enregistrement échoue, le contenu déjà écrit est supprimé.
     *
     * @param ownerId        identifiant de l'utilisateur connecté
     * @param file           fichier reçu en multipart
     * @param password       mot de passe optionnel ({@code null} ou vide = fichier non protégé)
     * @param expirationDays durée de validité du lien, de 1 à 7 jours
     * @return les métadonnées du fichier et son lien public
     * @throws ApiException          400 ou 413 si une règle de saisie n'est pas respectée
     * @throws UncheckedIOException  si l'écriture sur le stockage échoue (traduite en 500 par le gestionnaire d'erreurs)
     */
    public FileResponse upload(UUID ownerId, MultipartFile file, String password, int expirationDays) {
        validator.validate(file, password, expirationDays);

        Instant now = clock.instant();
        String passwordHash = (password == null || password.isEmpty()) ? null : passwordEncoder.encode(password);
        StoredFile stored = new StoredFile(ownerId, FileValidator.sanitizeName(file.getOriginalFilename()),
                contentTypeOf(file), file.getSize(), tokenGenerator.newDownloadToken(), passwordHash, now,
                now.plus(Duration.ofDays(expirationDays)));

        // Copie en flux : le fichier n'est jamais chargé entièrement en mémoire.
        // La taille retenue est celle réellement écrite, plus fiable que celle annoncée par le client.
        try (InputStream in = file.getInputStream()) {
            stored.updateSize(storage.store(stored.getStorageKey(), in));
        } catch (IOException ex) {
            throw new UncheckedIOException("Échec de l'écriture du fichier", ex);
        }
        try {
            files.saveAndFlush(stored);
        } catch (RuntimeException ex) {
            // La base a refusé l'enregistrement : on ne laisse pas de contenu orphelin sur le disque.
            deleteContentQuietly(stored);
            throw ex;
        }
        log.info("Fichier {} téléversé par {} ({} octets, expire le {})",
                stored.getId(), ownerId, stored.getSizeBytes(), stored.getExpiresAt());
        return toResponse(stored, now);
    }

    /**
     * Renvoie l'historique des fichiers de l'utilisateur, du plus récent au plus ancien (US05).
     *
     * @param ownerId identifiant de l'utilisateur connecté : on ne voit jamais les fichiers des autres
     * @param filter  {@code ALL}, {@code ACTIVE} (lien encore valide) ou {@code EXPIRED}
     * @return la liste filtrée, éventuellement vide
     */
    @Transactional(readOnly = true)
    public List<FileResponse> history(UUID ownerId, FileStatusFilter filter) {
        Instant now = clock.instant();
        List<StoredFile> result = switch (filter) {
            case ALL -> files.findByOwnerIdOrderByCreatedAtDesc(ownerId);
            case ACTIVE -> files.findByOwnerIdAndExpiresAtAfterOrderByCreatedAtDesc(ownerId, now);
            case EXPIRED -> files.findByOwnerIdAndExpiresAtLessThanEqualOrderByCreatedAtDesc(ownerId, now);
        };
        return result.stream().map(f -> toResponse(f, now)).toList();
    }

    /**
     * Supprime définitivement un fichier : métadonnées et contenu (US06).
     *
     * <p>Un fichier qui appartient à un autre utilisateur renvoie 404 et non 403, pour ne pas révéler
     * son existence. Les métadonnées sont supprimées en premier ({@code flush}) : si la base échoue,
     * le contenu est conservé et rien n'est perdu.
     *
     * @param ownerId identifiant de l'utilisateur connecté
     * @param fileId  identifiant du fichier à supprimer
     * @throws ApiException 404 {@code NOT_FOUND} si le fichier n'existe pas ou n'appartient pas à l'utilisateur ;
     *                      500 {@code STORAGE_ERROR} si le contenu n'a pas pu être effacé du disque
     */
    @Transactional
    public void delete(UUID ownerId, UUID fileId) {
        StoredFile file = files.findById(fileId)
                .filter(f -> f.getOwnerId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Fichier introuvable."));
        files.delete(file);
        files.flush();
        try {
            storage.delete(file.getStorageKey());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "Le fichier n'a pas pu être supprimé du stockage.");
        }
        log.info("Fichier {} supprimé par {}", fileId, ownerId);
    }

    /**
     * Convertit l'entité en réponse d'API. L'état « expiré » est calculé à l'instant {@code now}
     * plutôt que stocké, pour être toujours exact.
     */
    FileResponse toResponse(StoredFile f, Instant now) {
        return new FileResponse(f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                f.getCreatedAt(), f.getExpiresAt(), f.isExpiredAt(now), f.isProtected(), f.getDownloadToken(),
                publicUrl + "/d/" + f.getDownloadToken());
    }

    /** Supprime un contenu sans propager d'erreur : utilisé pendant une annulation, où l'erreur d'origine prime. */
    private void deleteContentQuietly(StoredFile file) {
        try {
            storage.delete(file.getStorageKey());
        } catch (IOException ex) {
            log.warn("Contenu orphelin non supprimé : {}", file.getStorageKey(), ex);
        }
    }

    /** Type MIME annoncé par le navigateur, ou {@link #DEFAULT_CONTENT_TYPE} s'il est absent ou trop long pour la colonne. */
    private static String contentTypeOf(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || type.isBlank() || type.length() > 255) {
            return DEFAULT_CONTENT_TYPE;
        }
        return type;
    }
}
