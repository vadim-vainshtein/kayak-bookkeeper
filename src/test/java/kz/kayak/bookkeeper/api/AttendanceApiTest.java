package kz.kayak.bookkeeper.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.security.AccountService;
import kz.kayak.bookkeeper.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AttendanceServiceTest.FixedClock.class)
@Transactional
class AttendanceApiTest {
    private static final String ADMIN = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired MockMvc mvc;
    @Autowired SubscriptionService subscriptions;
    @Autowired AccountService accounts;

    @Test
    void authenticatesAttendanceReadsAndRestrictsWritesToAdmins() throws Exception {
        long id = session();
        String path = "/api/sessions/" + id + "/attendance";
        accounts.createAccount(new CreateUserRequest("attendance-reader", "reader-password", null));
        String reader = "Basic " + Base64.getEncoder().encodeToString("attendance-reader:reader-password".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(0)).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.rateDate").value("2026-11-07"));
        mvc.perform(put(path).header("Authorization", reader).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"memberIds\":[],\"guests\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"memberIds\":[],\"guests\":[]}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/sessions/999999/attendance").header("Authorization", ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void validatesRevisionAndNestedGuestAmountsBeforeMutation() throws Exception {
        String path = "/api/sessions/" + session() + "/attendance";
        for (String body : List.of(
                "{\"memberIds\":[],\"guests\":[]}",
                "{\"revision\":-1,\"memberIds\":[],\"guests\":[]}",
                "{\"revision\":0,\"memberIds\":[null],\"guests\":[]}",
                "{\"revision\":0,\"memberIds\":[],\"guests\":[{\"personId\":1,\"amount\":-1}]}",
                "{\"revision\":0,\"memberIds\":[],\"guests\":[null]}")) {
            mvc.perform(put(path).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    private long session() {
        return subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(
                List.of(LocalDate.of(2026, 11, 6).atTime(19, 0)), 1)).sessions().getFirst().id();
    }
}
