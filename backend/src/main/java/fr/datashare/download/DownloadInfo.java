package fr.datashare.download;

import java.time.Instant;

/** Métadonnées visibles avant le téléchargement (US02). */
public record DownloadInfo(String originalName, String contentType, long sizeBytes, Instant expiresAt,
                           boolean passwordProtected) {
}
