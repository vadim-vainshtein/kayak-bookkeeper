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
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PaymentService;
import kz.kayak.bookkeeper.service.PersonService;
import kz.kayak.bookkeeper.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @Autowired SubscriptionRepository subscriptionRepository;

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

    @ParameterizedTest
    @EnumSource(SubscriptionStatus.class)
    void filtersEveryStatusBeforeCalculatingSummaryAndPaymentDetails(SubscriptionStatus filter) throws Exception {
        List<Subscription> subscriptions = statusFixtures();
        int index = filter.ordinal() + 1;
        long poolCost = filter == SubscriptionStatus.DRAFT ? 0 : index * 10;
        mockMvc.perform(get("/api/reports/financial").param("status", filter.name())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value(subscriptions.get(filter.ordinal()).getId()))
                .andExpect(jsonPath("$.subscriptions[0].status").value(filter.name()))
                .andExpect(jsonPath("$.subscriptions[0].payments.length()").value(1))
                .andExpect(jsonPath("$.subscriptions[0].payments[0].allocated").value(index * 30))
                .andExpect(jsonPath("$.summary.charged").value(index * 100))
                .andExpect(jsonPath("$.summary.allocatedPayments").value(index * 30))
                .andExpect(jsonPath("$.summary.outstanding").value(index * 70))
                .andExpect(jsonPath("$.summary.poolRentalCost").value(poolCost))
                .andExpect(jsonPath("$.summary.clubProfit").value(index * 30 - poolCost))
                .andExpect(jsonPath("$.unassignedCharges[0].amount").value(50));
    }

    @Test
    void statusIntersectsSelectedIdsAndEmptyIntersectionDoesNotFallBackToAll() throws Exception {
        List<Subscription> subscriptions = statusFixtures();
        mockMvc.perform(get("/api/reports/financial").param("status", "FINALIZED")
                        .param("subscriptionId", subscriptions.get(0).getId().toString(), subscriptions.get(1).getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value(subscriptions.get(1).getId()))
                .andExpect(jsonPath("$.summary.charged").value(200));
        mockMvc.perform(get("/api/reports/financial").param("status", "COMPLETED")
                        .param("subscriptionId", subscriptions.get(0).getId().toString(), subscriptions.get(1).getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions").isEmpty())
                .andExpect(jsonPath("$.summary.charged").value(0))
                .andExpect(jsonPath("$.summary.allocatedPayments").value(0))
                .andExpect(jsonPath("$.summary.outstanding").value(0))
                .andExpect(jsonPath("$.summary.poolRentalCost").value(0))
                .andExpect(jsonPath("$.summary.clubProfit").value(0))
                .andExpect(jsonPath("$.unassignedCharges[0].amount").value(50));
    }

    @Test
    void omittedStatusRetainsAllStatusesAndRejectsInvalidStatus() throws Exception {
        statusFixtures();
        mockMvc.perform(get("/api/reports/financial").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.length()").value(3))
                .andExpect(jsonPath("$.summary.charged").value(600))
                .andExpect(jsonPath("$.summary.allocatedPayments").value(180))
                .andExpect(jsonPath("$.summary.outstanding").value(420))
                .andExpect(jsonPath("$.summary.poolRentalCost").value(50))
                .andExpect(jsonPath("$.summary.clubProfit").value(130));
        mockMvc.perform(get("/api/reports/financial").param("status", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isBadRequest());
    }

    private List<Subscription> statusFixtures() {
        var person = personService.createPerson(new PersonRequest("Статусы", null, null));
        LocalDate date = LocalDate.of(2026, 11, 1);
        chargeService.createManualCharge(new ManualChargeRequest(person.id(), 50, date, "Без абонемента", null));
        return java.util.Arrays.stream(SubscriptionStatus.values()).map(state -> {
            int index = state.ordinal() + 1;
            var subscription = new Subscription(1, state.name(), null);
            if (state != SubscriptionStatus.DRAFT) subscription.finalizeWith(index * 10);
            if (state == SubscriptionStatus.COMPLETED) subscription.complete();
            subscription = subscriptionRepository.save(subscription);
            var charge = chargeService.createManualCharge(new ManualChargeRequest(person.id(), index * 100, date, "Начисление", subscription.getId()));
            var payment = paymentService.createPayment(new PaymentRequest(person.id(), index * 30, date, PaymentMethod.CASH, null));
            paymentService.allocatePayment(payment.id(), new AllocationRequest(charge.id(), index * 30));
            return subscription;
        }).toList();
    }
}
