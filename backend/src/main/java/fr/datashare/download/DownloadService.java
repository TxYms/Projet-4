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
 * Accès public à un fichier via son lien unique, tant que celui-ci est valide (US02).
 *
 * <p>Parcours d'un fichier protégé par mot de passe :
 * <ol>
 *   <li>{@link #info} : le destinataire voit le nom, la taille et l'expiration ;</li>
 *   <li>{@link #unlock} : il saisit le mot de passe et reçoit un ticket valable 5 minutes ;</li>
 *   <li>{@link #open} : le navigateur télécharge le contenu en présentant ce ticket.</li>
 * </ol>
 * Pour un fichier non protégé, l'étape 2 est inutile. Ce découpage évite de faire passer le mot de passe
 * dans l'URL, où il finirait dans les journaux du serveur et l'historique du navigateur.
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

    /**
     * Renvoie les métadonnées affichées avant le téléchargement.
     *
     * @param token lien de téléchargement (partie variable de l'URL {@code /d/{token}})
     * @return nom, type, taille, date d'expiration et présence d'un mot de passe
     * @throws ApiException 404 si le lien est inconnu, 410 s'il a expiré
     */
    @Transactional(readOnly = true)
    public DownloadInfo info(String token) {
        StoredFile file = findAvailable(token);
        return new DownloadInfo(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
                file.getExpiresAt(), file.isProtected());
    }

    /**
     * Vérifie le mot de passe et délivre un ticket de courte durée pour lancer le téléchargement.
     *
     * @param token    lien de téléchargement
     * @param password mot de passe saisi par le destinataire
     * @return un ticket JWT valable quelques minutes et pour ce seul lien
     * @throws ApiException 401 {@code WRONG_PASSWORD} si le mot de passe est faux ; 404 ou 410 si le lien n'est plus valide
     */
    @Transactional(readOnly = true)
    public TicketResponse unlock(String token, String password) {
        StoredFile file = findAvailable(token);
        if (file.isProtected() && !passwordEncoder.matches(password, file.getPasswordHash())) {
            // Journalisé pour pouvoir repérer d'éventuelles tentatives répétées (force brute).
            log.info("Mot de passe incorrect pour le lien {}", file.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WRONG_PASSWORD", "Mot de passe incorrect.");
        }
        return new TicketResponse(jwtService.issueDownloadTicket(token));
    }

    /**
     * Ouvre le contenu du fichier pour l'envoyer en flux au navigateur.
     *
     * @param token  lien de téléchargement
     * @param ticket ticket obtenu via {@link #unlock} ; ignoré si le fichier n'est pas protégé
     * @return le nom à proposer au navigateur, la taille et le flux du contenu
     * @throws ApiException 401 {@code PASSWORD_REQUIRED} si le fichier est protégé et le ticket absent ou invalide ;
     *                      404 ou 410 si le lien n'est plus valide ou si le contenu a disparu du disque
     */
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
            // Métadonnées présentes mais contenu absent : incohérence à investiguer, d'où le niveau ERROR.
            log.error("Contenu introuvable pour le fichier {}", file.getId(), ex);
            throw new ApiException(HttpStatus.GONE, "FILE_UNAVAILABLE", "Ce fichier n'est plus disponible.");
        }
    }

    /**
     * Point de contrôle commun aux trois étapes : le lien doit exister et ne pas avoir expiré.
     *
     * <p>Un lien inconnu renvoie 404, un lien expiré 410 (Gone) : le front affiche un message explicite
     * dans les deux cas, comme le demande la règle « lien expiré ou invalide → erreur explicite ».
     * L'expiration est vérifiée à chaque requête, sans attendre la purge nocturne.
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

    /**
     * Contenu prêt à être envoyé.
     *
     * @param fileName nom d'origine, proposé au navigateur
     * @param size     taille en octets, envoyée dans l'en-tête {@code Content-Length}
     * @param resource flux du contenu, lu au fil de l'envoi
     */
    public record Download(String fileName, long size, Resource resource) {
    }
}
