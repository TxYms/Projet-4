package fr.datashare.download;

import static fr.datashare.support.ApiClient.registerAndLogin;
import static fr.datashare.support.ApiClient.textFile;
import static fr.datashare.support.ApiClient.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class DownloadFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    String token;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(TestClockConfig.START);
        token = registerAndLogin(mvc, "dl-" + UUID.randomUUID() + "@example.com", "motdepasse");
    }

    @Test
    void anyone_with_the_link_sees_metadata_and_downloads() throws Exception {
        String link = JsonPath.read(upload(mvc, token, textFile("été 2026.txt", "contenu"), null, 7), "$.downloadToken");

        mvc.perform(get("/api/download/" + link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("été 2026.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(7))
                .andExpect(jsonPath("$.passwordProtected").value(false))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-09T10:00:00Z"));

        mvc.perform(get("/api/download/" + link + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("UTF-8''%C3%A9t%C3%A9%202026.txt")))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().string("contenu"));
    }

    @Test
    void unknown_link_returns_404() throws Exception {
        mvc.perform(get("/api/download/inexistant")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void expired_link_returns_410_with_explicit_message() throws Exception {
        String link = JsonPath.read(upload(mvc, token, textFile("vieux.txt", "x"), null, 1), "$.downloadToken");
        clock.advance(Duration.ofDays(1));

        mvc.perform(get("/api/download/" + link)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FILE_EXPIRED"))
                .andExpect(jsonPath("$.detail").value(containsString("expiré")));
        mvc.perform(get("/api/download/" + link + "/content")).andExpect(status().isGone());
    }

    @Test
    void protected_file_requires_password_then_ticket() throws Exception {
        String link = JsonPath.read(upload(mvc, token, textFile("secret.txt", "chut"), "sesame", 7), "$.downloadToken");

        mvc.perform(get("/api/download/" + link)).andExpect(jsonPath("$.passwordProtected").value(true));
        mvc.perform(get("/api/download/" + link + "/content")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
        mvc.perform(post("/api/download/" + link + "/unlock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        String ticketJson = mvc.perform(post("/api/download/" + link + "/unlock")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"sesame\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ticket = JsonPath.read(ticketJson, "$.ticket");

        mvc.perform(get("/api/download/" + link + "/content").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(content().string("chut"));
    }

    @Test
    void ticket_is_bound_to_one_file() throws Exception {
        String secret = JsonPath.read(upload(mvc, token, textFile("a.txt", "a"), "sesame", 7), "$.downloadToken");
        String other = JsonPath.read(upload(mvc, token, textFile("b.txt", "b"), "sesame", 7), "$.downloadToken");
        String ticketForOther = JsonPath.read(mvc.perform(post("/api/download/" + other + "/unlock")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"sesame\"}"))
                .andReturn().getResponse().getContentAsString(), "$.ticket");

        mvc.perform(get("/api/download/" + secret + "/content").param("ticket", ticketForOther))
                .andExpect(status().isUnauthorized());
        assertThat(ticketForOther).isNotBlank();
    }

    @Test
    void access_token_cannot_be_used_as_download_ticket() throws Exception {
        String link = JsonPath.read(upload(mvc, token, textFile("a.txt", "a"), "sesame", 7), "$.downloadToken");
        mvc.perform(get("/api/download/" + link + "/content").param("ticket", token))
                .andExpect(status().isUnauthorized());
    }
}
