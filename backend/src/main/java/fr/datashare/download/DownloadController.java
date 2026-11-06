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

@RestController
@RequestMapping("/api/download/{token}")
@Tag(name = "Téléchargement", description = "Accès public par lien unique (US02)")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping
    @Operation(summary = "Métadonnées du fichier avant téléchargement")
    public DownloadInfo info(@PathVariable String token) {
        return downloadService.info(token);
    }

    @PostMapping("/unlock")
    @Operation(summary = "Vérifier le mot de passe d'un fichier protégé et obtenir un ticket de 5 minutes")
    public TicketResponse unlock(@PathVariable String token, @Valid @RequestBody UnlockRequest request) {
        return downloadService.unlock(token, request.password());
    }

    @GetMapping("/content")
    @Operation(summary = "Télécharger le contenu (flux binaire)")
    public ResponseEntity<Resource> content(@PathVariable String token,
                                            @Parameter(description = "Ticket obtenu via /unlock, requis si le fichier est protégé")
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        Download download = downloadService.open(token, ticket);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        // Toujours servi en pièce jointe et en type binaire : le navigateur n'interprète jamais le contenu.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.size())
                .body(download.resource());
    }
}
