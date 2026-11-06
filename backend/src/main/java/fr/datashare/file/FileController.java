package fr.datashare.file;

import fr.datashare.config.OpenApiConfig;
import fr.datashare.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Mes fichiers", description = "Téléversement, historique et suppression (US01, US05, US06)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Téléverser un fichier (1 Go maximum)")
    public FileResponse upload(@AuthenticationPrincipal Jwt jwt,
                               @RequestPart("file") MultipartFile file,
                               @Parameter(description = "Mot de passe optionnel, 6 caractères minimum")
                               @RequestParam(value = "password", required = false) String password,
                               @Parameter(description = "Durée de validité en jours, de 1 à 7")
                               @RequestParam(value = "expirationDays", defaultValue = "7") int expirationDays) {
        return fileService.upload(CurrentUser.idOf(jwt), file, password, expirationDays);
    }

    @GetMapping
    @Operation(summary = "Historique des fichiers envoyés, du plus récent au plus ancien")
    public List<FileResponse> history(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(value = "status", defaultValue = "ALL") FileStatusFilter status) {
        return fileService.history(CurrentUser.idOf(jwt), status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer définitivement un fichier")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        fileService.delete(CurrentUser.idOf(jwt), id);
    }
}
