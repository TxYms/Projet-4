package fr.datashare.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corps de {@code POST /api/auth/login} (US04).
 * Seul le format est contrôlé ici ; la vérification du mot de passe est faite par {@link AuthService}.
 */
public record LoginRequest(
        @NotBlank(message = "L'e-mail est obligatoire.")
        @Email(message = "Le format de l'e-mail est invalide.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        String password) {
}
