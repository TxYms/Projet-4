package fr.datashare.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Métadonnées d'un fichier téléversé. Le contenu lui-même est dans le {@code StorageService}
 * sous la clé {@link #storageKey}.
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

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "download_token", nullable = false, unique = true)
    private String downloadToken;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "content_purged", nullable = false)
    private boolean contentPurged;

    protected StoredFile() {
        // requis par JPA
    }

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

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isProtected() {
        return passwordHash != null;
    }

    public void markContentPurged() {
        this.contentPurged = true;
    }

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
