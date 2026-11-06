package fr.datashare.file;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<StoredFile> findByOwnerIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID ownerId, Instant now);

    List<StoredFile> findByOwnerIdAndExpiresAtLessThanEqualOrderByCreatedAtDesc(UUID ownerId, Instant now);

    Optional<StoredFile> findByDownloadToken(String downloadToken);

    /** Fichiers expirés dont le contenu est encore sur le disque. */
    List<StoredFile> findByExpiresAtLessThanEqualAndContentPurgedFalse(Instant now);

    /** Fichiers expirés depuis plus longtemps que la durée de rétention de l'historique. */
    List<StoredFile> findByExpiresAtBefore(Instant limit);
}
