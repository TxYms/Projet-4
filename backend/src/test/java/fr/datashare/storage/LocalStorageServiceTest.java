package fr.datashare.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.datashare.config.DatashareProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

    @TempDir
    Path root;

    LocalStorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        storage = new LocalStorageService(new DatashareProperties("http://x", null, null,
                new DatashareProperties.Storage(root.toString()), null, null));
    }

    @Test
    void stores_loads_and_deletes_content() throws Exception {
        long written = storage.store("abc-123", new ByteArrayInputStream("bonjour".getBytes(StandardCharsets.UTF_8)));

        assertThat(written).isEqualTo(7);
        assertThat(storage.load("abc-123").getContentAsString(StandardCharsets.UTF_8)).isEqualTo("bonjour");
        assertThat(root.toFile().list()).containsExactly("abc-123");

        storage.delete("abc-123");
        assertThatThrownBy(() -> storage.load("abc-123")).isInstanceOf(NoSuchFileException.class);
        storage.delete("abc-123"); // idempotent
    }

    @Test
    void refuses_keys_that_could_escape_the_storage_folder() {
        assertThatThrownBy(() -> storage.load("../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.load(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
