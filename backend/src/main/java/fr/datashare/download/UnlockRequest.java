package fr.datashare.download;

import jakarta.validation.constraints.NotBlank;

/** Corps de {@code POST /api/download/{token}/unlock} : le mot de passe saisi par le destinataire. */
public record UnlockRequest(@NotBlank(message = "Le mot de passe est obligatoire.") String password) {
}
