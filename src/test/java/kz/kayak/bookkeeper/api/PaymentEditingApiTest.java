package kz.kayak.bookkeeper.api;

import java.time.LocalDate;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.service.BalanceService;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PaymentService;
import kz.kayak.bookkeeper.service.PersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentEditingApiTest {
    private static final String ADMIN = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);
    @Autowired MockMvc mvc;
    @Autowired PersonService people;
    @Autowired ChargeService charges;
    @Autowired PaymentService payments;
    @Autowired BalanceService balances;

    @Test
    void editsOnlyAmountAndAcceptsExactAllocatedTotal() throws Exception {
        var payment = allocatedPayment(100, 60);
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":60}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(60))
                .andExpect(jsonPath("$.personId").value(payment.personId()))
                .andExpect(jsonPath("$.paidAt").value(DATE.toString()))
                .andExpect(jsonPath("$.method").value("TRANSFER"))
                .andExpect(jsonPath("$.note").value("Банк"))
                .andExpect(jsonPath("$.allocations[0].amount").value(60));
        assertThat(balances.balance(payment.personId()).paid()).isEqualTo(60);
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":200}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(200));
    }

    @Test
    void rejectsAmountBelowCurrentAllocationWithoutChangingPayment() throws Exception {
        var payment = allocatedPayment(100, 60);
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":59,\"paidAt\":\"2026-11-04\",\"note\":\"Изменён\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Сумма оплаты не может быть меньше распределённой суммы: 60 ₸."));
        assertThat(balances.balance(payment.personId()).payments()).singleElement().satisfies(stored -> {
            assertThat(stored.amount()).isEqualTo(100);
            assertThat(stored.paidAt()).isEqualTo(DATE);
            assertThat(stored.note()).isEqualTo("Банк");
            assertThat(stored.allocations()).hasSize(1);
        });
    }

    @Test
    void editsDateAndCommentWhilePreservingMethodAndAllocations() throws Exception {
        var payment = allocatedPayment(100, 60);
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":120,\"paidAt\":\"2026-11-04\",\"note\":\"Исправленный комментарий\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(120))
                .andExpect(jsonPath("$.paidAt").value("2026-11-04"))
                .andExpect(jsonPath("$.note").value("Исправленный комментарий"))
                .andExpect(jsonPath("$.method").value("TRANSFER"));
        var stored = balances.balance(payment.personId()).payments().getFirst();
        assertThat(stored.paidAt()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(stored.note()).isEqualTo("Исправленный комментарий");
        assertThat(stored.allocations()).isEqualTo(payment.allocations());
        for (String input : new String[]{"{\"amount\":110}", "{\"amount\":100,\"paidAt\":null,\"note\":null}"}) {
            mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                            .contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paidAt").value("2026-11-04"))
                    .andExpect(jsonPath("$.note").value("Исправленный комментарий"));
        }
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100,\"note\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").doesNotExist());
        assertThat(balances.balance(payment.personId()).payments().getFirst().note()).isNull();
    }

    @Test
    void rejectsInvalidDateAndOversizedCommentWithoutChangingPayment() throws Exception {
        var payment = allocatedPayment(100, 60);
        for (String input : new String[]{
                "{\"amount\":120,\"paidAt\":\"not-a-date\"}",
                "{\"amount\":120,\"paidAt\":\"2026-13-01\"}",
                "{\"amount\":120,\"paidAt\":\"2026-11-04\",\"note\":\"%s\"}".formatted("a".repeat(1001))}) {
            mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                            .contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isBadRequest());
        }
        var stored = balances.balance(payment.personId()).payments().getFirst();
        assertThat(stored.amount()).isEqualTo(100);
        assertThat(stored.paidAt()).isEqualTo(DATE);
        assertThat(stored.note()).isEqualTo("Банк");
        assertThat(stored.allocations()).isEqualTo(payment.allocations());
    }

    @Test
    void validatesPositiveAmountAndMissingPayment() throws Exception {
        var payment = allocatedPayment(100, 60);
        for (String input : new String[]{"{}", "{\"amount\":null}", "{\"amount\":0}", "{\"amount\":-1}",
                "{\"amount\":\"invalid\"}", "{\"amount\":60.5}", "{\"amount\":\"60\"}", "{\"amount\":9223372036854775808}"}) {
            mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                            .contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balances.balance(payment.personId()).payments().getFirst().amount()).isEqualTo(100);
        mvc.perform(patch("/api/payments/{id}", Long.MAX_VALUE).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellationReleasesChargeAndPaymentAndAllowsReductionAndReallocation() throws Exception {
        var payment = allocatedPayment(100, 60);
        var allocation = payment.allocations().getFirst();
        mvc.perform(delete("/api/payments/{paymentId}/allocations/{allocationId}", payment.id(), allocation.id())
                        .header("Authorization", ADMIN))
                .andExpect(status().isNoContent());
        var balance = balances.balance(payment.personId());
        assertThat(balance.paid()).isEqualTo(100);
        assertThat(balance.payments().getFirst().allocations()).isEmpty();
        assertThat(balance.charges().getFirst().allocated()).isZero();
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(25));
        mvc.perform(post("/api/payments/{id}/allocations", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chargeId\":%d,\"amount\":25}".formatted(allocation.chargeId())))
                .andExpect(status().isNoContent());
        assertThat(balances.balance(payment.personId()).charges().getFirst().allocated()).isEqualTo(25);
    }

    @Test
    void minimumIncludesAllAllocationsAndCancellingOnePreservesTheOthers() throws Exception {
        var payment = allocatedPayment(100, 60);
        var otherCharge = charges.createManualCharge(new ManualChargeRequest(payment.personId(), 100, DATE, "Весло", null));
        payments.allocatePayment(payment.id(), new AllocationRequest(otherCharge.id(), 40));
        var allocations = balances.balance(payment.personId()).payments().getFirst().allocations();
        var secondAllocation = allocations.stream().filter(item -> item.chargeId().equals(otherCharge.id())).findFirst().orElseThrow();
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":99}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/payments/{paymentId}/allocations/{allocationId}", payment.id(), secondAllocation.id())
                        .header("Authorization", ADMIN)).andExpect(status().isNoContent());
        assertThat(balances.balance(payment.personId()).payments().getFirst().allocations())
                .singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(payment.allocations().getFirst().id()));
        mvc.perform(patch("/api/payments/{id}", payment.id()).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":60}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(60));
    }

    @Test
    void cannotCancelAnAllocationOfAnotherPaymentOrAnUnknownAllocation() throws Exception {
        var payment = allocatedPayment(100, 60);
        var other = payments.createPayment(new PaymentRequest(payment.personId(), 100, DATE, PaymentMethod.CASH, null));
        var allocation = payment.allocations().getFirst();
        mvc.perform(delete("/api/payments/{paymentId}/allocations/{allocationId}", other.id(), allocation.id())
                        .header("Authorization", ADMIN)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/payments/{paymentId}/allocations/{allocationId}", payment.id(), Long.MAX_VALUE)
                        .header("Authorization", ADMIN)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/payments/{paymentId}/allocations/{allocationId}", Long.MAX_VALUE, allocation.id())
                        .header("Authorization", ADMIN)).andExpect(status().isNotFound());
        assertThat(balances.balance(payment.personId()).charges().getFirst().allocated()).isEqualTo(60);
    }

    @Test
    void unauthenticatedRequestsCannotEditPaymentsOrCancelAllocations() throws Exception {
        mvc.perform(patch("/api/payments/1").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/payments/1/allocations/1")).andExpect(status().isUnauthorized());
    }

    private PaymentView allocatedPayment(long amount, long allocation) {
        var person = people.createPerson(new PersonRequest("Редактирование оплаты", null, null));
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 100, DATE, "Бассейн", null));
        return payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(
                person.id(), amount, DATE, PaymentMethod.TRANSFER, "Банк", charge.id(), allocation));
    }
}
