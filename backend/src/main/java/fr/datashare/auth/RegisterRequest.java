package fr.datashare.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US03 : e-mail valide et mot de passe d'au moins 8 caractères. */
public record RegisterRequest(
        @NotBlank(message = "L'e-mail est obligatoire.")
        @Email(message = "Le format de l'e-mail est invalide.")
        @Size(max = 255, message = "L'e-mail est trop long.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères.")
        String password) {
}
