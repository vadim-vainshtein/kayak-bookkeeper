package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.service.BookkeepingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinancialReportApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired private MockMvc mockMvc;
    @Autowired private BookkeepingService service;

    @Test
    void returnsAllTimeSubscriptionReportWithoutDateParameters() throws Exception {
        LocalDate november=LocalDate.of(2026,11,1); Api.PersonView person=service.createPerson(new Api.PersonRequest("Алия",null,null));
        service.createManualCharge(new Api.ManualChargeRequest(person.id(),5_000,november,"Ноябрьская тренировка",null));

        mockMvc.perform(get("/api/reports/financial").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").doesNotExist())
                .andExpect(jsonPath("$.unassignedCharges[0].personName").value("Алия"))
                .andExpect(jsonPath("$.unassignedCharges[0].amount").value(5_000));
    }

    @Test
    void rejectsAnUnknownSubscriptionFilter() throws Exception {
        mockMvc.perform(get("/api/reports/financial").param("subscriptionId","999999").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isNotFound());
    }

    @Test
    void includesPaymentAllocationsInTheSelectedSubscriptionDetails() throws Exception {
        LocalDate paidAt = LocalDate.of(2026, 11, 3);
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Алия", null, null));
        Api.SubscriptionView subscription = service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(
                List.of(paidAt.atTime(19, 0)), 1, "Ноябрь", null));
        Api.ChargeView charge = service.createManualCharge(new Api.ManualChargeRequest(person.id(), 5_000,
                paidAt, "Корректировка", subscription.id()));
        Api.PaymentView payment = service.createPayment(new Api.PaymentRequest(person.id(), 5_000, paidAt,
                PaymentMethod.TRANSFER, "Kaspi"));
        service.allocatePayment(payment.id(), new Api.AllocationRequest(charge.id(), 5_000));

        mockMvc.perform(get("/api/reports/financial").param("subscriptionId", subscription.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions[0].payments[0].paymentId").value(payment.id()))
                .andExpect(jsonPath("$.subscriptions[0].payments[0].personName").value("Алия"))
                .andExpect(jsonPath("$.subscriptions[0].payments[0].paidAt").value("2026-11-03"))
                .andExpect(jsonPath("$.subscriptions[0].payments[0].method").value("TRANSFER"))
                .andExpect(jsonPath("$.subscriptions[0].payments[0].chargeId").value(charge.id()))
                .andExpect(jsonPath("$.subscriptions[0].payments[0].allocated").value(5_000));
    }
}
