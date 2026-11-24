package fr.datashare.auth;

import fr.datashare.common.ApiException;
import fr.datashare.security.JwtService;
import fr.datashare.security.JwtService.IssuedToken;
import fr.datashare.user.User;
import fr.datashare.user.UserRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service métier de gestion des comptes : inscription (US03), connexion (US04) et lecture du profil courant.
 *
 * <p>Règles portées par ce service :
 * <ul>
 *   <li>l'e-mail est unique, sans tenir compte de la casse ni des espaces autour ;</li>
 *   <li>le mot de passe n'est jamais stocké en clair : seul son hachage BCrypt est conservé ;</li>
 *   <li>un échec de connexion ne révèle pas si le compte existe.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    /**
     * @param users           accès aux comptes en base
     * @param passwordEncoder encodeur BCrypt défini dans {@code SecurityConfig}
     * @param jwtService      émetteur des jetons d'accès
     * @param clock           horloge injectée, remplacée par une horloge contrôlable dans les tests
     */
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /**
     * Crée un compte utilisateur (US03).
     *
     * @param request e-mail et mot de passe, déjà validés par Bean Validation dans le contrôleur
     * @return le compte créé, sans aucune donnée sensible
     * @throws ApiException 409 {@code EMAIL_ALREADY_USED} si un compte existe déjà avec cet e-mail
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "Un compte existe déjà avec cet e-mail.");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), clock.instant());
        return UserResponse.from(users.save(user));
    }

    /**
     * Vérifie les identifiants et émet un jeton d'accès JWT (US04).
     *
     * <p>Le message d'erreur est identique que l'e-mail existe ou non, pour ne pas permettre de deviner
     * quels comptes existent (énumération de comptes).
     *
     * @param request e-mail et mot de passe saisis
     * @return le jeton d'accès, sa durée de validité en secondes et le profil de l'utilisateur
     * @throws ApiException 401 {@code INVALID_CREDENTIALS} si l'e-mail est inconnu ou le mot de passe faux
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "E-mail ou mot de passe incorrect."));
        IssuedToken token = jwtService.issueAccessToken(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }

    /**
     * Renvoie le profil de l'utilisateur connecté.
     *
     * @param userId identifiant lu dans le jeton d'accès
     * @return le profil de l'utilisateur
     * @throws ApiException 401 {@code UNKNOWN_USER} si le compte a été supprimé depuis l'émission du jeton
     */
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER", "Session invalide."));
    }

    /**
     * Met l'e-mail sous une forme canonique (sans espaces autour, en minuscules) pour que
     * {@code Alice@Example.com} et {@code alice@example.com} désignent le même compte.
     * {@link Locale#ROOT} évite les surprises liées à la langue du serveur (le « i » turc, par exemple).
     */
    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
