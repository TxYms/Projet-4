package fr.datashare.auth;

import static fr.datashare.support.ApiClient.bearer;
import static fr.datashare.support.ApiClient.credentials;
import static fr.datashare.support.ApiClient.registerAndLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.datashare.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void register_creates_account_without_exposing_password() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("Alice@Example.com", "motdepasse")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_rejects_duplicate_email_case_insensitively() throws Exception {
        registerAndLogin(mvc, "bob@example.com", "motdepasse");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("BOB@example.com", "autremotdepasse")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void register_validates_email_and_password_length() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("pas-un-email", "court")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_returns_jwt_and_me_returns_current_user() throws Exception {
        String token = registerAndLogin(mvc, "carole@example.com", "motdepasse");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carole@example.com"));
    }

    @Test
    void login_uses_same_message_for_unknown_email_and_wrong_password() throws Exception {
        registerAndLogin(mvc, "david@example.com", "motdepasse");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("david@example.com", "mauvais-mdp")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("inconnu@example.com", "motdepasse")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protected_routes_require_a_valid_token() throws Exception {
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/files").header("Authorization", "Bearer faux.jeton.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
