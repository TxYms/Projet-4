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

    /** Émetteur inscrit dans chaque jeton ({@code iss}) et vérifié au décodage. */
    static final String ISSUER = "datashare";
    /** Revendication qui lie un ticket à un lien de téléchargement précis. */
    private static final String FILE_CLAIM = "file";

    private final JwtEncoder encoder;
    private final JwtDecoder ticketDecoder;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration ticketTtl;

    /**
     * @param downloadTicketDecoder décodeur dédié aux tickets. Le {@code @Qualifier} est indispensable :
     *                              sans lui, Spring injecterait le décodeur des jetons d'accès (marqué
     *                              {@code @Primary}) et aucun ticket ne serait jamais accepté.
     */
    public JwtService(JwtEncoder encoder, @Qualifier("downloadTicketDecoder") JwtDecoder downloadTicketDecoder, Clock clock,
                      DatashareProperties properties) {
        this.encoder = encoder;
        this.ticketDecoder = downloadTicketDecoder;
        this.clock = clock;
        this.accessTtl = properties.jwt().accessTokenTtl();
        this.ticketTtl = properties.jwt().downloadTicketTtl();
    }

    /**
     * Émet le jeton d'accès remis à l'utilisateur après sa connexion.
     *
     * @param user utilisateur authentifié ; son identifiant devient le sujet ({@code sub}) du jeton
     * @return le jeton signé et sa durée de validité en secondes (1 heure par défaut)
     */
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

    /**
     * Émet un ticket de téléchargement après vérification du mot de passe d'un fichier.
     *
     * @param downloadToken lien du fichier auquel le ticket donne accès, et à lui seul
     * @return le ticket signé, valable 5 minutes par défaut
     */
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

    /**
     * Vérifie qu'un ticket est valide, non expiré et émis pour ce lien précis.
     *
     * @param ticket        ticket présenté par le navigateur, éventuellement absent
     * @param downloadToken lien demandé
     * @return {@code true} seulement si toutes les vérifications passent ; aucune exception n'est levée
     */
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

    /**
     * Jeton émis.
     *
     * @param value            jeton JWT signé, à envoyer dans l'en-tête {@code Authorization: Bearer}
     * @param expiresInSeconds durée de validité, transmise au front pour qu'il sache quand la session expire
     */
    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
