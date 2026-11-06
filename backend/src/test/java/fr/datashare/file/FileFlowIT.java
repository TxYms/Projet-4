package fr.datashare.file;

import static fr.datashare.support.ApiClient.bearer;
import static fr.datashare.support.ApiClient.login;
import static fr.datashare.support.ApiClient.registerAndLogin;
import static fr.datashare.support.ApiClient.textFile;
import static fr.datashare.support.ApiClient.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.datashare.support.IntegrationTest;
import fr.datashare.support.MutableClock;
import fr.datashare.support.TestClockConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class FileFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StoredFileRepository repository;

    String email;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(TestClockConfig.START);
        email = "user-" + UUID.randomUUID() + "@example.com";
        token = registerAndLogin(mvc, email, "motdepasse");
    }

    @Test
    void upload_returns_unique_link_and_expiration() throws Exception {
        String json = upload(mvc, token, textFile("rapport.txt", "bonjour"), null, 3);

        assertThat(JsonPath.<String>read(json, "$.downloadToken")).hasSizeGreaterThanOrEqualTo(22);
        assertThat(JsonPath.<String>read(json, "$.downloadUrl")).startsWith("http://localhost:4200/d/");
        assertThat(JsonPath.<String>read(json, "$.expiresAt")).isEqualTo("2026-11-05T10:00:00Z");
        assertThat(JsonPath.<Integer>read(json, "$.sizeBytes")).isEqualTo(7);
        assertThat(JsonPath.<Boolean>read(json, "$.passwordProtected")).isFalse();
    }

    @Test
    void two_uploads_never_share_the_same_link() throws Exception {
        String a = JsonPath.read(upload(mvc, token, textFile("a.txt", "a"), null, 7), "$.downloadToken");
        String b = JsonPath.read(upload(mvc, token, textFile("a.txt", "a"), null, 7), "$.downloadToken");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void upload_rejects_forbidden_extension_short_password_and_bad_expiration() throws Exception {
        mvc.perform(multipart("/api/files").file(textFile("virus.EXE", "x")).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_FILE_TYPE"));
        mvc.perform(multipart("/api/files").file(textFile("ok.txt", "x")).param("password", "123")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        mvc.perform(multipart("/api/files").file(textFile("ok.txt", "x")).param("expirationDays", "8")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPIRATION"));
    }

    @Test
    void upload_requires_authentication() throws Exception {
        mvc.perform(multipart("/api/files").file(textFile("ok.txt", "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void history_lists_files_and_filters_by_status() throws Exception {
        upload(mvc, token, textFile("court.txt", "1"), null, 1);
        clock.advance(Duration.ofMinutes(1));
        upload(mvc, token, textFile("long.txt", "2"), null, 7);
        clock.advance(Duration.ofDays(2));
        // Le jeton d'accès (1 h) a expiré entre-temps : l'utilisateur se reconnecte
        token = login(mvc, email, "motdepasse");

        mvc.perform(get("/api/files").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].originalName").value("long.txt"))
                .andExpect(jsonPath("$[1].expired").value(true));
        mvc.perform(get("/api/files").param("status", "ACTIVE").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalName").value("long.txt"));
        mvc.perform(get("/api/files").param("status", "EXPIRED").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalName").value("court.txt"));
    }

    @Test
    void history_only_shows_own_files() throws Exception {
        upload(mvc, token, textFile("moi.txt", "1"), null, 7);
        String other = registerAndLogin(mvc, "autre-" + UUID.randomUUID() + "@example.com", "motdepasse");
        mvc.perform(get("/api/files").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void delete_removes_file_and_link() throws Exception {
        String json = upload(mvc, token, textFile("a-supprimer.txt", "x"), null, 7);
        String id = JsonPath.read(json, "$.id");
        String link = JsonPath.read(json, "$.downloadToken");

        mvc.perform(delete("/api/files/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(UUID.fromString(id))).isEmpty();
        mvc.perform(get("/api/download/" + link)).andExpect(status().isNotFound());
    }

    @Test
    void user_cannot_delete_someone_else_file() throws Exception {
        String id = JsonPath.read(upload(mvc, token, textFile("prive.txt", "x"), null, 7), "$.id");
        String other = registerAndLogin(mvc, "pirate-" + UUID.randomUUID() + "@example.com", "motdepasse");

        mvc.perform(delete("/api/files/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        assertThat(repository.findById(UUID.fromString(id))).isPresent();
    }
}
