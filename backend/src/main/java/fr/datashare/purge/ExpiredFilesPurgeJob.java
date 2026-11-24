package fr.datashare.purge;

import fr.datashare.config.DatashareProperties;
import fr.datashare.file.StoredFile;
import fr.datashare.file.StoredFileRepository;
import fr.datashare.storage.StorageService;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purge planifiée des fichiers expirés (règle de gestion de US01).
 * <ol>
 *   <li>Dès l'expiration, le lien est refusé (contrôle fait à chaque requête par {@code DownloadService}).</li>
 *   <li>Chaque nuit, le contenu des fichiers expirés est supprimé du stockage.</li>
 *   <li>Les métadonnées restent visibles dans l'historique (onglet « Expiré » des maquettes) pendant la durée
 *   de rétention, puis sont supprimées à leur tour.</li>
 * </ol>
 */
@Component
public class ExpiredFilesPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredFilesPurgeJob.class);

    private final StoredFileRepository files;
    private final StorageService storage;
    private final Clock clock;
    private final Duration metadataRetention;

    public ExpiredFilesPurgeJob(StoredFileRepository files, StorageService storage, Clock clock,
                                DatashareProperties properties) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
        this.metadataRetention = properties.purge().metadataRetention();
    }

    /**
     * Lance une purge. Déclenchée automatiquement selon {@code datashare.purge.cron} (chaque nuit à 3 h),
     * et appelée directement par les tests.
     *
     * <p>Idempotente : relancée deux fois de suite, la seconde exécution ne trouve plus rien à faire.
     * Un contenu qui n'a pas pu être supprimé reste marqué non purgé et sera retenté à la purge suivante.
     *
     * @return le nombre de contenus et d'entrées d'historique supprimés, aussi écrit dans le journal
     */
    @Scheduled(cron = "${datashare.purge.cron}")
    @Transactional
    public PurgeReport purge() {
        Instant now = clock.instant();

        // Étape 1 : fichiers expirés dont le contenu est encore sur le disque.
        List<StoredFile> expired = files.findByExpiresAtLessThanEqualAndContentPurgedFalse(now);
        int contents = 0;
        for (StoredFile file : expired) {
            if (deleteContent(file)) {
                // La modification de l'entité est enregistrée à la fin de la transaction.
                file.markContentPurged();
                contents++;
            }
        }

        // Étape 2 : entrées d'historique plus anciennes que la durée de rétention.
        // Le contenu est supprimé une seconde fois par sécurité (sans effet s'il n'existe plus).
        List<StoredFile> old = files.findByExpiresAtBefore(now.minus(metadataRetention));
        old.forEach(this::deleteContent);
        files.deleteAll(old);

        PurgeReport report = new PurgeReport(contents, old.size());
        log.info("Purge des fichiers expirés : {} contenus supprimés, {} entrées d'historique supprimées",
                report.contentsDeleted(), report.metadataDeleted());
        return report;
    }

    /** @return {@code true} si le contenu a été supprimé (ou n'existait déjà plus), {@code false} en cas d'erreur disque */
    private boolean deleteContent(StoredFile file) {
        try {
            storage.delete(file.getStorageKey());
            return true;
        } catch (IOException ex) {
            log.warn("Impossible de supprimer le contenu du fichier {}, nouvel essai à la prochaine purge",
                    file.getId(), ex);
            return false;
        }
    }

    /**
     * Bilan d'une purge.
     *
     * @param contentsDeleted contenus supprimés du disque (étape 1)
     * @param metadataDeleted entrées d'historique supprimées de la base (étape 2)
     */
    public record PurgeReport(int contentsDeleted, int metadataDeleted) {
    }
}
