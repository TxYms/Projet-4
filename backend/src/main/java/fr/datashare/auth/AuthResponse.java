package fr.datashare.auth;

/**
 * Réponse de connexion (US04).
 *
 * @param accessToken jeton JWT à envoyer dans l'en-tête {@code Authorization} des requêtes suivantes
 * @param tokenType   toujours {@code Bearer}
 * @param expiresIn   durée de validité du jeton, en secondes
 * @param user        profil de l'utilisateur connecté
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
