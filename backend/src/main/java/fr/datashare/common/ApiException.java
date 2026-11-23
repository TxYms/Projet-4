package fr.datashare.common;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier traduite en réponse HTTP (format Problem Details, RFC 9457) par {@link GlobalExceptionHandler}.
 * Le code permet au front d'afficher un message adapté sans analyser le texte.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
