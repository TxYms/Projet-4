package fr.datashare.security;

import fr.datashare.config.DatashareProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuration de sécurité de l'API.
 *
 * <p>API sans état : pas de session ni de cookie, chaque requête protégée porte un jeton JWT
 * dans l'en-tête {@code Authorization: Bearer}. Le CSRF est donc désactivé (pas de cookie à détourner).
 *
 * <p>Principe retenu : <b>tout est fermé par défaut</b>. Seules les routes listées explicitement sont
 * publiques ; toute nouvelle route sous {@code /api} exige un jeton, et toute autre URL est refusée.
 */
@Configuration
public class SecurityConfig {

    /**
     * Chaîne de filtres appliquée à chaque requête HTTP.
     *
     * <p>L'ordre des {@code requestMatchers} compte : la première règle qui correspond l'emporte.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Applique la configuration CORS définie plus bas (corsConfigurationSource).
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Routes publiques : inscription, connexion, téléchargement par lien, documentation, santé.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/download/**").permitAll()
                        .requestMatchers("/api/docs/**", "/actuator/health").permitAll()
                        // Requêtes de pré-vérification CORS envoyées par le navigateur, sans jeton.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Tout le reste de l'API exige un jeton d'accès valide.
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                // Vérification des JWT par le décodeur marqué @Primary (jetons d'accès uniquement).
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .headers(headers -> headers
                        // L'API ne sert que du JSON et des fichiers : aucune ressource à charger, aucun affichage en iframe.
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        // Empêche le lien de téléchargement de fuiter vers un site tiers via l'en-tête Referer.
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /** Encodeur des mots de passe des comptes et des fichiers protégés. */
    @Bean
    PasswordEncoder passwordEncoder() {
        // BCrypt : hachage lent et salé automatiquement (US03)
        return new BCryptPasswordEncoder();
    }

    /** Signe les jetons émis par {@link JwtService} avec la clé HMAC-SHA256 de {@link JwtKeys}. */
    @Bean
    JwtEncoder jwtEncoder(JwtKeys keys) {
        return NimbusJwtEncoder.withSecretKey(keys.key()).algorithm(MacAlgorithm.HS256).build();
    }

    /** Décodeur des jetons d'accès, utilisé par Spring Security sur les routes protégées. */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(JwtKeys keys, Clock clock) {
        return decoderFor(keys, clock, TokenType.ACCESS);
    }

    /** Décodeur des tickets de téléchargement, utilisé uniquement par {@link JwtService}. */
    @Bean
    JwtDecoder downloadTicketDecoder(JwtKeys keys, Clock clock) {
        return decoderFor(keys, clock, TokenType.DOWNLOAD);
    }

    /**
     * Construit un décodeur qui n'accepte qu'un seul type de jeton.
     *
     * <p>Un jeton est accepté seulement si sa signature, ses dates, son émetteur et son type ({@code typ})
     * sont tous valides. Le contrôle du type empêche d'utiliser un ticket de téléchargement comme jeton
     * de connexion, et inversement, alors qu'ils sont signés avec la même clé.
     */
    private static JwtDecoder decoderFor(JwtKeys keys, Clock clock, TokenType type) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(keys.key()).macAlgorithm(MacAlgorithm.HS256).build();
        // L'expiration est vérifiée avec la même horloge que celle qui émet les jetons
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(JwtService.ISSUER),
                new JwtClaimValidator<String>(TokenType.CLAIM, type.value()::equals));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Autorise le front, servi depuis une autre origine en production, à appeler l'API.
     * Seules les origines de {@code datashare.cors.allowed-origins} sont acceptées. En développement,
     * le proxy d'Angular fait passer les appels par la même origine et CORS n'intervient pas.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(DatashareProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Rend le nom du fichier téléchargé lisible par le front.
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
