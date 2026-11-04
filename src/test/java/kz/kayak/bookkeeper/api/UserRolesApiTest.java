package kz.kayak.bookkeeper.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.UserRole;
import kz.kayak.bookkeeper.repository.UserAccountRepository;
import kz.kayak.bookkeeper.security.AccountService;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PersonService;
import kz.kayak.bookkeeper.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserRolesApiTest {
    private static final String PASSWORD = "club-member-password";
    private static final String ADMIN = auth("admin", "test-admin-password");
    private static final String MEMBER = auth("member", PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountService accounts;
    @Autowired UserAccountRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired PersonService people;
    @Autowired ChargeService charges;
    @Autowired SubscriptionService subscriptions;

    @Test
    void adminCreatesReadOnlyAccountWithHashedPasswordAndSafeResponses() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "member", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("member"))
                .andExpect(jsonPath("$.role").value("READ_ONLY"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var saved = repository.findByLogin("member").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        mvc.perform(get("/api/account").header("Authorization", MEMBER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("READ_ONLY"));
        mvc.perform(get("/api/users").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].passwordHash").isEmpty());
    }

    @Test
    void memberReadsClubDataIncludingBalancesFinancialReportsAndDocumentation() throws Exception {
        member();
        var person = people.createPerson(new PersonRequest("Алия", "700", "Заметка"));
        var subscription = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(
                List.of(LocalDate.of(2026, 11, 5).atTime(19, 0)), 1, "Ноябрь", null));
        charges.createManualCharge(new ManualChargeRequest(person.id(), 5_000, LocalDate.of(2026, 11, 1), "Тренировка", null));
        for (String path : List.of("/api/account", "/api/people", "/api/people/balances", "/api/people/" + person.id() + "/balance",
                "/api/pricing-rates", "/api/subscriptions", "/api/subscriptions/" + subscription.id(),
                "/api/sessions/" + subscription.sessions().getFirst().id() + "/roster", "/api/reports/financial", "/v3/api-docs", "/swagger-ui/index.html")) {
            mvc.perform(get(path).header("Authorization", MEMBER)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/reports/financial").header("Authorization", MEMBER))
                .andExpect(jsonPath("$.unassignedCharges[0].personName").value("Алия"))
                .andExpect(jsonPath("$.unassignedCharges[0].amount").value(5_000));
        mvc.perform(head("/api/people").header("Authorization", MEMBER)).andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", MEMBER)).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsCanReadKayakerContactsAndNotesWithoutChangingStoredDetails() throws Exception {
        member();
        var first = people.createPerson(new PersonRequest("Алия", "+77001234567", "Личная заметка"));
        var second = people.createPerson(new PersonRequest("Борис", "+77007654321", "Другой комментарий"));
        people.updatePerson(second.id(), new PersonUpdateRequest(second.fullName(), second.phone(), second.note(), false));

        mvc.perform(get("/api/people").header("Authorization", MEMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.id()))
                .andExpect(jsonPath("$[0].fullName").value("Алия"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].phone").value(nullValue()))
                .andExpect(jsonPath("$[0].note").value(nullValue()))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].phone").value(nullValue()))
                .andExpect(jsonPath("$[1].note").value(nullValue()));
        mvc.perform(get("/api/people").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phone").value(first.phone()))
                .andExpect(jsonPath("$[0].note").value(first.note()))
                .andExpect(jsonPath("$[1].phone").value(second.phone()))
                .andExpect(jsonPath("$[1].note").value(second.note()));
        mvc.perform(put("/api/people/" + first.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new PersonUpdateRequest("Алия", "+77001111111", "Новая заметка", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+77001111111"))
                .andExpect(jsonPath("$.note").value("Новая заметка"));
        mvc.perform(get("/api/people").header("Authorization", MEMBER))
                .andExpect(jsonPath("$[0].phone").value(nullValue()))
                .andExpect(jsonPath("$[0].note").value(nullValue()));
        assertThat(people.listPeople().getFirst().phone()).isEqualTo("+77001111111");
        assertThat(people.listPeople().getFirst().note()).isEqualTo("Новая заметка");
    }

    @Test
    void memberCannotMutateAnyEndpointEvenWithDirectApiRequests() throws Exception {
        member();
        List<String> endpoints = List.of(
                "POST /api/people", "PUT /api/people/1", "POST /api/pricing-rates", "PUT /api/pricing-rates/1/effective-to", "DELETE /api/pricing-rates/1",
                "POST /api/subscriptions", "POST /api/subscriptions/recurring", "POST /api/subscriptions/recurring-with-members",
                "PUT /api/subscriptions/1/metadata", "PUT /api/subscriptions/1/draft", "PUT /api/subscriptions/1/finalized",
                "POST /api/subscriptions/1/finalized-edit-preview", "POST /api/subscriptions/1/members", "POST /api/subscriptions/1/finalize", "POST /api/subscriptions/1/complete",
                "PUT /api/sessions/1/attendance", "POST /api/sessions/1/drop-ins", "POST /api/charges", "PUT /api/charges/1",
                "POST /api/payments", "POST /api/payments/with-allocation", "POST /api/payments/1/allocations",
                "PATCH /api/payments/1", "DELETE /api/payments/1/allocations/1",
                "PUT /api/account/password", "POST /api/users", "PATCH /api/future-endpoint", "DELETE /api/future-endpoint");
        for (String endpoint : endpoints) {
            String[] parts = endpoint.split(" ");
            mvc.perform(request(HttpMethod.valueOf(parts[0]), parts[1]).header("Authorization", MEMBER)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(encoder.matches(PASSWORD, repository.findByLogin("member").orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateAnotherAdminAndExistingAdminRetainsWriteAccess() throws Exception {
        mvc.perform(get("/api/account").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(post("/api/users").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "second-admin", "password", PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(post("/api/people").header("Authorization", auth("second-admin", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Новый каякер\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateAndInvalidAccountInputWithoutCreatingUsers() throws Exception {
        member();
        long before = repository.count();
        mvc.perform(post("/api/users").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "member", "password", PASSWORD))))
                .andExpect(status().isConflict());
        for (Map<String, Object> input : List.<Map<String, Object>>of(
                Map.of("login", "", "password", PASSWORD), Map.of("login", "with space", "password", PASSWORD),
                Map.of("login", "with:colon", "password", PASSWORD), Map.of("login", "a".repeat(101), "password", PASSWORD),
                Map.of("login", "new", "password", "short"), Map.of("login", "new", "password", "a".repeat(73)),
                Map.of("login", "new", "password", "я".repeat(37)), Map.of("login", "new", "password", PASSWORD, "role", "OWNER"))) {
            mvc.perform(post("/api/users").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isEqualTo(before);
    }

    private void member() {
        accounts.createAccount(new CreateUserRequest("member", PASSWORD, UserRole.READ_ONLY));
    }

    private static String auth(String login, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
