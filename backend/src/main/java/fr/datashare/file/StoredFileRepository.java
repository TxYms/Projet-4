package fr.datashare.file;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux métadonnées des fichiers.
 *
 * <p>Les requêtes sont dérivées du nom des méthodes par Spring Data JPA : aucun SQL n'est écrit à la main.
 * Les index de la migration {@code V1} couvrent les deux usages principaux (historique par propriétaire,
 * recherche par date d'expiration).
 */
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    /** Historique complet d'un utilisateur, du plus récent au plus ancien (filtre « Tous »). */
    List<StoredFile> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /** Fichiers dont le lien est encore valide à l'instant donné (filtre « Actifs »). */
    List<StoredFile> findByOwnerIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID ownerId, Instant now);

    /** Fichiers expirés à l'instant donné (filtre « Expiré »). */
    List<StoredFile> findByOwnerIdAndExpiresAtLessThanEqualOrderByCreatedAtDesc(UUID ownerId, Instant now);

    /** Recherche par lien public, utilisée par la page de téléchargement. */
    Optional<StoredFile> findByDownloadToken(String downloadToken);

    /** Fichiers expirés dont le contenu est encore sur le disque. */
    List<StoredFile> findByExpiresAtLessThanEqualAndContentPurgedFalse(Instant now);

    /** Fichiers expirés depuis plus longtemps que la durée de rétention de l'historique. */
    List<StoredFile> findByExpiresAtBefore(Instant limit);
}
