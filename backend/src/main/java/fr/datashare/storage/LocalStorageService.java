package fr.datashare.storage;

import fr.datashare.config.DatashareProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Stockage sur le système de fichiers local.
 * Les fichiers sont enregistrés sous une clé générée par l'application (jamais sous le nom fourni par
 * l'utilisateur) : impossible d'écrire hors du dossier de stockage ou d'écraser un autre fichier.
 */
@Service
public class LocalStorageService implements StorageService {

    /** Seules les clés faites de lettres, chiffres et tirets sont acceptées : ni « / », ni « .. ». */
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9-]{1,100}");
    private final Path root;

    /**
     * @param properties fournit le dossier racine ({@code datashare.storage.root-dir}), créé s'il n'existe pas
     * @throws IOException si le dossier ne peut pas être créé : l'application refuse alors de démarrer
     */
    public LocalStorageService(DatashareProperties properties) throws IOException {
        this.root = Path.of(properties.storage().rootDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le contenu est d'abord écrit dans un fichier temporaire, puis renommé en une seule opération :
     * un téléversement interrompu ne laisse jamais un fichier à moitié écrit sous sa clé définitive.
     */
    @Override
    public long store(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Path tmp = Files.createTempFile(root, "upload-", ".part");
        try {
            long written = Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return written;
        } finally {
            // Sans effet si le renommage a réussi ; nettoie le fichier temporaire en cas d'erreur.
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public Resource load(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchFileException(key);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /**
     * Transforme une clé en chemin à l'intérieur du dossier de stockage.
     *
     * @throws IllegalArgumentException si la clé pourrait désigner un fichier hors du dossier
     */
    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Clé de stockage invalide");
        }
        return root.resolve(key);
    }
}
