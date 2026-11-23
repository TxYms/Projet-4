package fr.datashare.security;

import fr.datashare.config.DatashareProperties;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Clé HMAC-SHA256 partagée par l'émission et la vérification des jetons.
 */
@Component
public class JwtKeys {

    private static final int MIN_KEY_BYTES = 32;
    private final SecretKey key;

    public JwtKeys(DatashareProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.jwt().secret());
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("datashare.jwt.secret doit faire au moins 32 octets (256 bits).");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public SecretKey key() {
        return key;
    }
}
