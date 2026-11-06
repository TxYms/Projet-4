package fr.datashare.common;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Génère les identifiants de lien de téléchargement.
 * 16 octets aléatoires (128 bits) issus d'un générateur cryptographique : le lien n'est pas prédictible
 * et ne peut pas être deviné par énumération.
 */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 16;
    private final SecureRandom random = new SecureRandom();

    public String newDownloadToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
