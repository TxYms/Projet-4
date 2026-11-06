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

    @Scheduled(cron = "${datashare.purge.cron}")
    @Transactional
    public PurgeReport purge() {
        Instant now = clock.instant();

        List<StoredFile> expired = files.findByExpiresAtLessThanEqualAndContentPurgedFalse(now);
        int contents = 0;
        for (StoredFile file : expired) {
            if (deleteContent(file)) {
                file.markContentPurged();
                contents++;
            }
        }

        List<StoredFile> old = files.findByExpiresAtBefore(now.minus(metadataRetention));
        old.forEach(this::deleteContent);
        files.deleteAll(old);

        PurgeReport report = new PurgeReport(contents, old.size());
        log.info("Purge des fichiers expirés : {} contenus supprimés, {} entrées d'historique supprimées",
                report.contentsDeleted(), report.metadataDeleted());
        return report;
    }

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

    public record PurgeReport(int contentsDeleted, int metadataDeleted) {
    }
}
