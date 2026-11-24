package fr.datashare.auth;

import fr.datashare.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Profil public d'un utilisateur. Ne contient volontairement pas le hachage du mot de passe :
 * l'entité {@link User} n'est jamais renvoyée telle quelle par l'API.
 */
public record UserResponse(UUID id, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
