package fr.datashare.download;

import fr.datashare.download.DownloadService.Download;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Points d'entrée REST publics du téléchargement, sous {@code /api/download/{token}} (US02).
 *
 * <p>Ces routes ne demandent pas de compte : posséder le lien suffit. La protection repose sur le
 * caractère non devinable du lien, sur l'expiration et, le cas échéant, sur le mot de passe.
 */
@RestController
@RequestMapping("/api/download/{token}")
@Tag(name = "Téléchargement", description = "Accès public par lien unique (US02)")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * {@code GET /api/download/{token}} : métadonnées affichées avant le téléchargement.
     *
     * @return 200 ; 404 si le lien est inconnu ; 410 s'il a expiré
     */
    @GetMapping
    @Operation(summary = "Métadonnées du fichier avant téléchargement")
    public DownloadInfo info(@PathVariable String token) {
        return downloadService.info(token);
    }

    /**
     * {@code POST /api/download/{token}/unlock} : échange le mot de passe contre un ticket de téléchargement.
     *
     * @return 200 avec le ticket ; 401 si le mot de passe est faux
     */
    @PostMapping("/unlock")
    @Operation(summary = "Vérifier le mot de passe d'un fichier protégé et obtenir un ticket de 5 minutes")
    public TicketResponse unlock(@PathVariable String token, @Valid @RequestBody UnlockRequest request) {
        return downloadService.unlock(token, request.password());
    }

    /**
     * {@code GET /api/download/{token}/content} : envoie le fichier en flux.
     *
     * <p>Spring copie la {@link Resource} vers la réponse par morceaux : un fichier de 1 Go ne passe jamais
     * entièrement en mémoire.
     *
     * @param ticket requis seulement si le fichier est protégé
     * @return 200 avec le contenu binaire ; 401 si le ticket manque ; 404 ou 410 si le lien n'est plus valide
     */
    @GetMapping("/content")
    @Operation(summary = "Télécharger le contenu (flux binaire)")
    public ResponseEntity<Resource> content(@PathVariable String token,
                                            @Parameter(description = "Ticket obtenu via /unlock, requis si le fichier est protégé")
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        Download download = downloadService.open(token, ticket);
        // Nom encodé en UTF-8 (RFC 6266) pour conserver les accents du nom d'origine.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        // Toujours servi en pièce jointe et en type binaire : le navigateur n'interprète jamais le contenu
        // (un fichier HTML ou SVG malveillant ne peut donc pas s'exécuter dans la page).
        // no-store : le fichier n'est pas gardé en cache, il reste soumis à l'expiration du lien.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.size())
                .body(download.resource());
    }
}
