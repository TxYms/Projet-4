package fr.datashare.file;

import java.time.Instant;
import java.util.UUID;

/** Élément de l'historique (US05) et réponse au téléversement (US01). */
public record FileResponse(
        UUID id,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        Instant expiresAt,
        boolean expired,
        boolean passwordProtected,
        String downloadToken,
        String downloadUrl) {
}
