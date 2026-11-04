package kz.kayak.bookkeeper.api;

import java.time.LocalDate;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PaymentService;
import kz.kayak.bookkeeper.service.PersonService;
import kz.kayak.bookkeeper.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinancialReportApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired private MockMvc mockMvc;
    @Autowired ChargeService chargeService;
    @Autowired PaymentService paymentService;
    @Autowired PersonService personService;
    @Autowired SubscriptionService subscriptionService;

    @Test
    void returnsAllTimeSubscriptionReportWithoutDateParameters() throws Exception {
        LocalDate november=LocalDate.of(2026,11,1); PersonView person=personService.createPerson(new PersonRequest("Алия",null,null));
        chargeService.createManualCharge(new ManualChargeRequest(person.id(),5_000,november,"Ноябрьская тренировка",null));

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
        PersonView person = personService.createPerson(new PersonRequest("Алия", null, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(
                List.of(paidAt.atTime(19, 0)), 1, "Ноябрь", null));
        ChargeView charge = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 5_000,
                paidAt, "Корректировка", subscription.id()));
        PaymentView payment = paymentService.createPayment(new PaymentRequest(person.id(), 5_000, paidAt,
                PaymentMethod.TRANSFER, "Kaspi"));
        paymentService.allocatePayment(payment.id(), new AllocationRequest(charge.id(), 5_000));

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
