package kz.kayak.bookkeeper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityApiTest {
    private static final String INITIAL_ADMIN_PASSWORD = "test-admin-password";
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectsApiAndAcceptsConfiguredInitialAdmin() throws Exception {
        mockMvc.perform(get("/api/people"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/people").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk());
    }

    @Test
    void forwardsRootToTheFrontendEntryPointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        MvcResult entryPoint = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kayak Bookkeeper")))
                .andExpect(content().string(containsString("/assets/")))
                .andExpect(content().string(not(containsString("webjars"))))
                .andReturn();
        Matcher asset = Pattern.compile("src=\\\"(/assets/[^\\\"]+\\.js)\\\"").matcher(entryPoint.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(asset.find(), "Vite entry script must be referenced by index.html");
        mockMvc.perform(get(asset.group(1)))
                .andExpect(status().isOk());
    }

    @Test
    void changesCurrentUsersPasswordAndInvalidatesOldCredentials() throws Exception {
        String newPassword = "new-secret-password";
        String newAuthorization = "Basic " + java.util.Base64.getEncoder().encodeToString(("admin:" + newPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(put("/api/account/password")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + INITIAL_ADMIN_PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/people").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/people").header(HttpHeaders.AUTHORIZATION, newAuthorization))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnIncorrectOrTooShortNewPassword() throws Exception {
        mockMvc.perform(put("/api/account/password")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorrect\",\"newPassword\":\"long-enough-password\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/account/password")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + INITIAL_ADMIN_PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
