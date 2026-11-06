package fr.datashare.file;

import fr.datashare.common.ApiException;
import fr.datashare.config.DatashareProperties;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contrôles de saisie du téléversement (US01), refaits côté serveur même s'ils existent côté front.
 */
@Component
public class FileValidator {

    static final long MAX_SIZE_BYTES = 1024L * 1024 * 1024;
    static final int MIN_PASSWORD_LENGTH = 6;
    static final int MAX_NAME_LENGTH = 255;

    private final Set<String> forbiddenExtensions;
    private final int maxExpirationDays;

    public FileValidator(DatashareProperties properties) {
        this.forbiddenExtensions = properties.files().normalizedForbiddenExtensions();
        this.maxExpirationDays = properties.files().maxExpirationDays();
    }

    public void validate(MultipartFile file, String password, int expirationDays) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Aucun fichier n'a été envoyé ou le fichier est vide.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                    "Le fichier dépasse la taille maximale de 1 Go.");
        }
        String name = sanitizeName(file.getOriginalFilename());
        String extension = extensionOf(name);
        if (forbiddenExtensions.contains(extension)) {
            throw ApiException.badRequest("FORBIDDEN_FILE_TYPE",
                    "Ce type de fichier n'est pas autorisé (." + extension + ").");
        }
        if (password != null && !password.isEmpty() && password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("PASSWORD_TOO_SHORT",
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        if (expirationDays < 1 || expirationDays > maxExpirationDays) {
            throw ApiException.badRequest("INVALID_EXPIRATION",
                    "La durée d'expiration doit être comprise entre 1 et " + maxExpirationDays + " jours.");
        }
    }

    /**
     * Garde uniquement le nom du fichier (sans chemin) et retire les caractères de contrôle.
     * Le nom n'est jamais utilisé comme chemin sur le disque, seulement pour l'affichage et le téléchargement.
     */
    public static String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "fichier";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            return "fichier";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }

    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
