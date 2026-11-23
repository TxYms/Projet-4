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
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final StoredFileRepository files;
    private final StorageService storage;
    private final FileValidator validator;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
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
     * US01. Volontairement hors transaction : la copie d'un fichier volumineux ne doit pas monopoliser
     * une connexion à la base. L'enregistrement des métadonnées a lieu une fois le contenu écrit.
     */
    public FileResponse upload(UUID ownerId, MultipartFile file, String password, int expirationDays) {
        validator.validate(file, password, expirationDays);

        Instant now = clock.instant();
        String passwordHash = (password == null || password.isEmpty()) ? null : passwordEncoder.encode(password);
        StoredFile stored = new StoredFile(ownerId, FileValidator.sanitizeName(file.getOriginalFilename()),
                contentTypeOf(file), file.getSize(), tokenGenerator.newDownloadToken(), passwordHash, now,
                now.plus(Duration.ofDays(expirationDays)));

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
     * US06 : suppression physique et irréversible. Un fichier d'un autre utilisateur renvoie 404 et non 403,
     * pour ne pas révéler son existence.
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

    FileResponse toResponse(StoredFile f, Instant now) {
        return new FileResponse(f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                f.getCreatedAt(), f.getExpiresAt(), f.isExpiredAt(now), f.isProtected(), f.getDownloadToken(),
                publicUrl + "/d/" + f.getDownloadToken());
    }

    private void deleteContentQuietly(StoredFile file) {
        try {
            storage.delete(file.getStorageKey());
        } catch (IOException ex) {
            log.warn("Contenu orphelin non supprimé : {}", file.getStorageKey(), ex);
        }
    }

    private static String contentTypeOf(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || type.isBlank() || type.length() > 255) {
            return DEFAULT_CONTENT_TYPE;
        }
        return type;
    }
}
