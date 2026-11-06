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

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /** US03 : crée un compte. L'e-mail est normalisé pour garantir son unicité. */
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
     * US04 : vérifie les identifiants et émet un jeton d'accès.
     * Le message d'erreur est identique que l'e-mail existe ou non, pour ne pas révéler quels comptes existent.
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

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER", "Session invalide."));
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
