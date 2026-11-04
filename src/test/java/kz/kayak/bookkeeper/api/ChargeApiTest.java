package kz.kayak.bookkeeper.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PersonService;
import kz.kayak.bookkeeper.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChargeApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";

    @Autowired
    private MockMvc mockMvc;
    @Autowired ChargeService chargeService;
    @Autowired PersonService personService;
    @Autowired SubscriptionService subscriptionService;

    @Test
    void attachesChargeWhenSubscriptionIdIsProvidedWithoutUpdateFlag() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 1);
        PersonView person = personService.createPerson(new PersonRequest("Алия", null, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026, 11, 2, 19, 0)), 1, "Ноябрь", null));
        ChargeView charge = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 5_000, date, "Корректировка", null));

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
