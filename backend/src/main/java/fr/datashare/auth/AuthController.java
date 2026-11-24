package fr.datashare.auth;

import fr.datashare.config.OpenApiConfig;
import fr.datashare.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Points d'entrée REST de l'authentification, sous {@code /api/auth}.
 *
 * <p>Le contrôleur ne contient aucune règle métier : il valide le corps de la requête ({@code @Valid})
 * et délègue à {@link AuthService}. Les erreurs sont converties en réponses JSON par
 * {@code GlobalExceptionHandler}.
 *
 * <p>{@code /register} et {@code /login} sont publics ; {@code /me} exige un jeton (voir {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Création de compte et connexion (US03, US04)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * {@code POST /api/auth/register} : crée un compte (US03).
     *
     * @return 201 avec le compte créé ; 400 si la saisie est invalide ; 409 si l'e-mail est déjà utilisé
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un compte")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * {@code POST /api/auth/login} : connecte l'utilisateur (US04).
     *
     * @return 200 avec le jeton d'accès ; 401 si les identifiants sont incorrects
     */
    @PostMapping("/login")
    @Operation(summary = "Se connecter et obtenir un jeton d'accès JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * {@code GET /api/auth/me} : profil de l'utilisateur connecté.
     *
     * @param jwt jeton d'accès déjà vérifié par Spring Security et injecté ici
     * @return 200 avec le profil ; 401 sans jeton valide
     */
    @GetMapping("/me")
    @Operation(summary = "Utilisateur connecté", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(CurrentUser.idOf(jwt));
    }
}
