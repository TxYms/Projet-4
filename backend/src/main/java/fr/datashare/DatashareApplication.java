package fr.datashare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée de l'API DataShare.
 *
 * <ul>
 *   <li>{@code @ConfigurationPropertiesScan} charge les réglages {@code datashare.*} dans {@link fr.datashare.config.DatashareProperties} ;</li>
 *   <li>{@code @EnableScheduling} active la purge nocturne des fichiers expirés.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DatashareApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatashareApplication.class, args);
    }
}
