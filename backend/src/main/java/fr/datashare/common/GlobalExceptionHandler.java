package fr.datashare.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Centralise la traduction des erreurs en réponses JSON homogènes (Problem Details).
 * Aucun détail technique (stack trace, requête SQL) n'est renvoyé au client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Erreurs métier levées volontairement par les services : statut, code et message déjà prêts. */
    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    /** Échec de Bean Validation sur un corps JSON : renvoie un message par champ, dans {@code errors}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Certains champs sont invalides.");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Fichier refusé par Spring avant même d'atteindre le contrôleur (limite {@code max-file-size}). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Le fichier dépasse la taille maximale de 1 Go.");
    }

    /** Requête mal construite (paramètre manquant, JSON illisible, filtre inconnu…) : 400 générique. */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class, MultipartException.class})
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "La requête est mal formée.");
    }

    /**
     * Filet de sécurité pour toute erreur imprévue : la trace complète est écrite dans le journal du serveur,
     * le client ne reçoit qu'un message générique.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erreur inattendue", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur inattendue est survenue. Réessayez plus tard.");
    }

    /** Construit une réponse Problem Details enrichie du champ {@code code}, que le front utilise pour réagir. */
    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setType(URI.create("https://datashare.fr/errors/" + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);
        return pd;
    }
}
