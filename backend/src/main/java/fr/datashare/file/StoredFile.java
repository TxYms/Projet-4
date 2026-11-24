package fr.datashare.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Métadonnées d'un fichier téléversé (table {@code stored_files}). Le contenu lui-même est dans le
 * {@code StorageService} sous la clé {@link #storageKey}.
 *
 * <p>Le schéma de la table est créé par la migration Flyway {@code V1__init_schema.sql} ; Hibernate vérifie
 * au démarrage que l'entité y correspond ({@code ddl-auto: validate}) mais ne modifie jamais la base.
 */
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Nom du fichier sur le disque, généré par l'application (jamais le nom fourni par l'utilisateur). */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    /** Partie variable du lien public {@code /d/{token}} : 128 bits aléatoires. */
    @Column(name = "download_token", nullable = false, unique = true)
    private String downloadToken;

    /** Hachage BCrypt du mot de passe du fichier, {@code null} si le fichier n'est pas protégé. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Passe à {@code true} quand la purge a supprimé le contenu du disque ; la ligne reste pour l'historique. */
    @Column(name = "content_purged", nullable = false)
    private boolean contentPurged;

    protected StoredFile() {
        // requis par JPA
    }

    /**
     * Crée les métadonnées d'un nouveau fichier. L'identifiant est généré ici, et la clé de stockage
     * en est dérivée : le nom sur le disque est donc connu avant même l'enregistrement en base.
     */
    public StoredFile(UUID ownerId, String originalName, String contentType, long sizeBytes, String downloadToken,
                      String passwordHash, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = id.toString();
        this.downloadToken = downloadToken;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * @param now instant de référence, fourni par l'horloge de l'application
     * @return {@code true} à partir de l'instant exact d'expiration (inclus)
     */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** @return {@code true} si un mot de passe est exigé pour télécharger le fichier */
    public boolean isProtected() {
        return passwordHash != null;
    }

    /** Marque le contenu comme supprimé du disque par la purge. */
    public void markContentPurged() {
        this.contentPurged = true;
    }

    /** Remplace la taille annoncée par le client par la taille réellement écrite sur le disque. */
    public void updateSize(long size) {
        this.sizeBytes = size;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getDownloadToken() {
        return downloadToken;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isContentPurged() {
        return contentPurged;
    }
}
