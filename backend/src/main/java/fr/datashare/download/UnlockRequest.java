package fr.datashare.download;

import jakarta.validation.constraints.NotBlank;

public record UnlockRequest(@NotBlank(message = "Le mot de passe est obligatoire.") String password) {
}
