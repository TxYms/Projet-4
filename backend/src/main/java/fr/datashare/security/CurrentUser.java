package fr.datashare.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lit l'identifiant de l'utilisateur connecté dans son jeton d'accès (revendication {@code sub}).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID idOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
