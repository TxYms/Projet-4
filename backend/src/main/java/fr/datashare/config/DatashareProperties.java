package fr.datashare.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration applicative regroupée sous le préfixe {@code datashare}.
 */
@Validated
@ConfigurationProperties(prefix = "datashare")
public record DatashareProperties(
        @NotBlank String publicUrl,
        Cors cors,
        Jwt jwt,
        Storage storage,
        Files files,
        Purge purge) {

    /** @param allowedOrigins origines autorisées à appeler l'API depuis un navigateur */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param secret            clé de signature en Base64 (au moins 32 octets), fournie par {@code JWT_SECRET}
     * @param accessTokenTtl    durée de validité des jetons d'accès
     * @param downloadTicketTtl durée de validité des tickets de téléchargement
     */
    public record Jwt(@NotBlank String secret, Duration accessTokenTtl, Duration downloadTicketTtl) {
    }

    /** @param rootDir dossier où sont écrits les contenus des fichiers */
    public record Storage(@NotBlank String rootDir) {
    }

    /**
     * @param defaultExpirationDays durée proposée par défaut au téléversement
     * @param maxExpirationDays     durée maximale autorisée (7 jours d'après les spécifications)
     * @param forbiddenExtensions   extensions refusées au téléversement, modifiables sans recompiler
     */
    public record Files(@Min(1) int defaultExpirationDays, @Min(1) int maxExpirationDays,
                        List<String> forbiddenExtensions) {

        /** Extensions interdites, normalisées en minuscules et sans point. */
        public Set<String> normalizedForbiddenExtensions() {
            return forbiddenExtensions == null ? Set.of()
                    : forbiddenExtensions.stream()
                            .map(ext -> ext.trim().toLowerCase().replaceFirst("^\\.", ""))
                            .filter(ext -> !ext.isEmpty())
                            .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * @param cron              planification de la purge (expression cron Spring, « - » pour la désactiver)
     * @param metadataRetention durée pendant laquelle un fichier expiré reste visible dans l'historique
     */
    public record Purge(String cron, Duration metadataRetention) {
    }
}
