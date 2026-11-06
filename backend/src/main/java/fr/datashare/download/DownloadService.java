package fr.datashare.download;

import fr.datashare.common.ApiException;
import fr.datashare.file.StoredFile;
import fr.datashare.file.StoredFileRepository;
import fr.datashare.security.JwtService;
import fr.datashare.storage.StorageService;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US02 : accès public à un fichier via son lien unique, tant que celui-ci est valide.
 */
@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    private final StoredFileRepository files;
    private final StorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public DownloadService(StoredFileRepository files, StorageService storage, PasswordEncoder passwordEncoder,
                           JwtService jwtService, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DownloadInfo info(String token) {
        StoredFile file = findAvailable(token);
        return new DownloadInfo(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
                file.getExpiresAt(), file.isProtected());
    }

    /** Vérifie le mot de passe et délivre un ticket de courte durée pour lancer le téléchargement. */
    @Transactional(readOnly = true)
    public TicketResponse unlock(String token, String password) {
        StoredFile file = findAvailable(token);
        if (file.isProtected() && !passwordEncoder.matches(password, file.getPasswordHash())) {
            log.info("Mot de passe incorrect pour le lien {}", file.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WRONG_PASSWORD", "Mot de passe incorrect.");
        }
        return new TicketResponse(jwtService.issueDownloadTicket(token));
    }

    @Transactional(readOnly = true)
    public Download open(String token, String ticket) {
        StoredFile file = findAvailable(token);
        if (file.isProtected() && !jwtService.isValidTicketFor(ticket, token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PASSWORD_REQUIRED",
                    "Ce fichier est protégé : saisissez le mot de passe pour le télécharger.");
        }
        try {
            return new Download(file.getOriginalName(), file.getSizeBytes(), storage.load(file.getStorageKey()));
        } catch (IOException ex) {
            log.error("Contenu introuvable pour le fichier {}", file.getId(), ex);
            throw new ApiException(HttpStatus.GONE, "FILE_UNAVAILABLE", "Ce fichier n'est plus disponible.");
        }
    }

    /**
     * Un lien inconnu renvoie 404, un lien expiré 410 (Gone) : le front affiche un message explicite
     * dans les deux cas, comme le demande la règle « lien expiré ou invalide → erreur explicite ».
     */
    private StoredFile findAvailable(String token) {
        StoredFile file = files.findByDownloadToken(token)
                .orElseThrow(() -> ApiException.notFound("Ce lien de téléchargement n'existe pas."));
        if (file.isExpiredAt(clock.instant()) || file.isContentPurged()) {
            throw new ApiException(HttpStatus.GONE, "FILE_EXPIRED",
                    "Ce fichier n'est plus disponible en téléchargement car il a expiré.");
        }
        return file;
    }

    public record Download(String fileName, long size, Resource resource) {
    }
}
