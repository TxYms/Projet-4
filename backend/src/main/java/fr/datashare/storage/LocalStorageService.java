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

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9-]{1,100}");
    private final Path root;

    public LocalStorageService(DatashareProperties properties) throws IOException {
        this.root = Path.of(properties.storage().rootDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public long store(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Path tmp = Files.createTempFile(root, "upload-", ".part");
        try {
            long written = Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return written;
        } finally {
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

    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Clé de stockage invalide");
        }
        return root.resolve(key);
    }
}
