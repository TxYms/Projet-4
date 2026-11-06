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

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(@NotBlank String secret, Duration accessTokenTtl, Duration downloadTicketTtl) {
    }

    public record Storage(@NotBlank String rootDir) {
    }

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

    public record Purge(String cron, Duration metadataRetention) {
    }
}
