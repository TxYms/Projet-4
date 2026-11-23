package fr.datashare.security;

/**
 * Valeur de la revendication {@code typ} : empêche d'utiliser un ticket de téléchargement comme jeton d'accès,
 * et inversement.
 */
public enum TokenType {
    ACCESS("access"),
    DOWNLOAD("download");

    public static final String CLAIM = "typ";
    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
