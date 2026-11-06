package fr.datashare.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Raccourcis pour écrire des scénarios de test lisibles. */
public final class ApiClient {

    private ApiClient() {
    }

    public static String registerAndLogin(MockMvc mvc, String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isCreated());
        return login(mvc, email, password);
    }

    public static String login(MockMvc mvc, String email, String password) throws Exception {
        String body = credentials(email, password);
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    public static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    public static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    /** Téléverse un fichier et renvoie la réponse JSON. */
    public static String upload(MockMvc mvc, String token, MockMultipartFile file, String password, int days)
            throws Exception {
        var request = multipart("/api/files").file(file)
                .param("expirationDays", String.valueOf(days))
                .header("Authorization", "Bearer " + token);
        if (password != null) {
            request.param("password", password);
        }
        return mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
