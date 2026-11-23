package fr.datashare.security;

import fr.datashare.config.DatashareProperties;
import fr.datashare.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Émet les jetons signés de l'application :
 * <ul>
 *   <li>jeton d'accès (US03/US04) : identifie l'utilisateur sur les routes protégées ;</li>
 *   <li>ticket de téléchargement (US02) : prouve, pendant quelques minutes, que le mot de passe d'un fichier
 *   protégé a été vérifié. Il évite de faire transiter le mot de passe dans l'URL de téléchargement.</li>
 * </ul>
 */
@Service
public class JwtService {

    static final String ISSUER = "datashare";
    private static final String FILE_CLAIM = "file";

    private final JwtEncoder encoder;
    private final JwtDecoder ticketDecoder;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration ticketTtl;

    public JwtService(JwtEncoder encoder, @Qualifier("downloadTicketDecoder") JwtDecoder downloadTicketDecoder, Clock clock,
                      DatashareProperties properties) {
        this.encoder = encoder;
        this.ticketDecoder = downloadTicketDecoder;
        this.clock = clock;
        this.accessTtl = properties.jwt().accessTokenTtl();
        this.ticketTtl = properties.jwt().downloadTicketTtl();
    }

    public IssuedToken issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("email", user.getEmail())
                .claim(TokenType.CLAIM, TokenType.ACCESS.value())
                .build();
        return new IssuedToken(encode(claims), accessTtl.toSeconds());
    }

    public String issueDownloadTicket(String downloadToken) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(downloadToken)
                .issuedAt(now)
                .expiresAt(now.plus(ticketTtl))
                .claim(FILE_CLAIM, downloadToken)
                .claim(TokenType.CLAIM, TokenType.DOWNLOAD.value())
                .build();
        return encode(claims);
    }

    /** Vérifie qu'un ticket est valide, non expiré et émis pour ce lien précis. */
    public boolean isValidTicketFor(String ticket, String downloadToken) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        try {
            Jwt jwt = ticketDecoder.decode(ticket);
            return Optional.ofNullable(jwt.getClaimAsString(FILE_CLAIM)).map(downloadToken::equals).orElse(false);
        } catch (JwtException ex) {
            return false;
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
