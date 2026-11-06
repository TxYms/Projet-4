package fr.datashare.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * Abstraction du stockage des contenus. L'implémentation locale peut être remplacée par un stockage objet
 * (AWS S3, MinIO) sans toucher aux services métier.
 */
public interface StorageService {

    /** Copie le flux vers le stockage sous la clé donnée et renvoie le nombre d'octets écrits. */
    long store(String key, InputStream content) throws IOException;

    Resource load(String key) throws IOException;

    /** Supprime le contenu. Ne lève pas d'erreur si la clé n'existe plus. */
    void delete(String key) throws IOException;
}
