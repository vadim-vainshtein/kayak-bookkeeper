package kz.kayak.bookkeeper.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import kz.kayak.bookkeeper.api.dto.CreateUserRequest;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import kz.kayak.bookkeeper.security.AccountService;
import kz.kayak.bookkeeper.service.AttendanceServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@Import(AttendanceServiceTest.FixedClock.class)
@Transactional
class AttendanceReportApiTest {
    private static final String ADMIN = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired MockMvc mvc;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired AccountService accounts;

    @Test
    void authenticatesReportAndAllowsReadOnlyAccounts() throws Exception {
        var subscription = finalized("Отчёт");
        accounts.createAccount(new CreateUserRequest("attendance-report-reader", "reader-password", null));
        String reader = "Basic " + Base64.getEncoder().encodeToString("attendance-report-reader:reader-password".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/reports/attendance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/attendance").header("Authorization", reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Qyzylorda"))
                .andExpect(jsonPath("$.generatedAt").value("2026-11-07T12:00:00"))
                .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value(subscription.getId()))
                .andExpect(jsonPath("$.summary.totalVisits").value(0));
    }

    @Test
    void statusAndSelectedIdsIntersectAndDraftsAreExcluded() throws Exception {
        var first = finalized("Рассчитанный");
        var completed = finalized("Завершённый");
        completed.complete();
        subscriptions.save(completed);
        subscriptions.save(new Subscription(1, "Черновик", null));
        mvc.perform(get("/api/reports/attendance").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subscriptions.length()").value(2));
        mvc.perform(get("/api/reports/attendance").header("Authorization", ADMIN)
                        .param("subscriptionId", first.getId().toString(), completed.getId().toString()).param("status", "COMPLETED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value(completed.getId()));
        mvc.perform(get("/api/reports/attendance").header("Authorization", ADMIN)
                        .param("subscriptionId", first.getId().toString()).param("status", "COMPLETED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subscriptions").isEmpty());
    }

    @Test
    void rejectsUnsupportedStatusMalformedIdAndUnknownSelection() throws Exception {
        for (String invalid : new String[]{"DRAFT", "UNKNOWN"})
            mvc.perform(get("/api/reports/attendance").header("Authorization", ADMIN).param("status", invalid))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/attendance").header("Authorization", ADMIN).param("subscriptionId", "not-a-number"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/attendance").header("Authorization", ADMIN).param("subscriptionId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    private Subscription finalized(String title) {
        var subscription = new Subscription(1, title, null);
        subscription.finalizeWith(1000);
        return subscriptions.save(subscription);
    }
}
