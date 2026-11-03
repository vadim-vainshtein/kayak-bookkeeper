package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.service.BookkeepingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChargeApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46YWRtaW4=";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BookkeepingService service;

    @Test
    void attachesChargeWhenSubscriptionIdIsProvidedWithoutUpdateFlag() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 1);
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Алия", null, null));
        Api.SubscriptionView subscription = service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026, 11, 2, 19, 0)), 1, "Ноябрь", null));
        Api.ChargeView charge = service.createManualCharge(new Api.ManualChargeRequest(person.id(), 5_000, date, "Корректировка", null));

        mockMvc.perform(put("/api/charges/{id}", charge.id())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":5000,"chargedAt":"2026-11-01","description":"Корректировка","subscriptionId":%d}
                                """.formatted(subscription.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value(subscription.id()));
    }
}
