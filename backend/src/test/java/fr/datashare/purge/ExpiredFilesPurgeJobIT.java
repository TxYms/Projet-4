package fr.datashare.purge;

import static fr.datashare.support.ApiClient.registerAndLogin;
import static fr.datashare.support.ApiClient.textFile;
import static fr.datashare.support.ApiClient.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import fr.datashare.file.StoredFile;
import fr.datashare.file.StoredFileRepository;
import fr.datashare.storage.StorageService;
import fr.datashare.support.IntegrationTest;
import fr.datashare.support.MutableClock;
import fr.datashare.support.TestClockConfig;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ExpiredFilesPurgeJobIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    ExpiredFilesPurgeJob job;

    @Autowired
    StoredFileRepository repository;

    @Autowired
    StorageService storage;

    @Test
    void purges_content_at_expiration_then_metadata_after_retention() throws Exception {
        clock.set(TestClockConfig.START);
        String token = registerAndLogin(mvc, "purge-" + UUID.randomUUID() + "@example.com", "motdepasse");
        UUID shortId = UUID.fromString(JsonPath.read(upload(mvc, token, textFile("court.txt", "x"), null, 1), "$.id"));
        UUID longId = UUID.fromString(JsonPath.read(upload(mvc, token, textFile("long.txt", "y"), null, 7), "$.id"));

        clock.advance(Duration.ofDays(2));
        job.purge();

        StoredFile expired = repository.findById(shortId).orElseThrow();
        assertThat(expired.isContentPurged()).isTrue();
        assertThatThrownBy(() -> storage.load(expired.getStorageKey())).isInstanceOf(NoSuchFileException.class);
        assertThat(repository.findById(longId).orElseThrow().isContentPurged()).isFalse();

        clock.advance(Duration.ofDays(30));
        job.purge();

        assertThat(repository.findById(shortId)).isEmpty();
        assertThat(repository.findById(longId)).isPresent();
    }
}
