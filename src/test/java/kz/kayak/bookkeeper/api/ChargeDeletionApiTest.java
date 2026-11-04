package kz.kayak.bookkeeper.api;

import java.time.LocalDate;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.domain.UserRole;
import kz.kayak.bookkeeper.security.AccountService;
import kz.kayak.bookkeeper.service.BalanceService;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PaymentService;
import kz.kayak.bookkeeper.service.PersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChargeDeletionApiTest {
    private static final String ADMIN = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);
    @Autowired MockMvc mvc;
    @Autowired PersonService people;
    @Autowired ChargeService charges;
    @Autowired PaymentService payments;
    @Autowired BalanceService balances;
    @Autowired AccountService accounts;

    @Test
    void deletesChargeAndRecalculatesBalanceWhilePreservingPaymentsAndOtherCharges() throws Exception {
        var person = people.createPerson(new PersonRequest("Удаление начисления", null, null));
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 500, DATE, "Ошибка", null));
        var other = charges.createManualCharge(new ManualChargeRequest(person.id(), 200, DATE, "Бассейн", null));
        var payment = payments.createPayment(new PaymentRequest(person.id(), 100, DATE, PaymentMethod.CASH, "Оплата"));

        mvc.perform(delete("/api/charges/{id}", charge.id()).header("Authorization", ADMIN))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        var balance = balances.balance(person.id());
        assertThat(balance.charged()).isEqualTo(200);
        assertThat(balance.paid()).isEqualTo(100);
        assertThat(balance.balance()).isEqualTo(100);
        assertThat(balance.charges()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(other.id()));
        assertThat(balance.payments()).containsExactly(payment);
        mvc.perform(delete("/api/charges/{id}", charge.id()).header("Authorization", ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAllocatedChargeUntilAllocationIsCancelledWithoutDeletingPayment() throws Exception {
        var person = people.createPerson(new PersonRequest("Распределённая оплата", null, null));
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 500, DATE, "Ошибка", null));
        var payment = payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(
                person.id(), 500, DATE, PaymentMethod.CASH, null, charge.id(), 200L));

        mvc.perform(delete("/api/charges/{id}", charge.id()).header("Authorization", ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя удалить начисление с распределённой оплатой. Сначала отмените распределение в окне редактирования оплаты."));
        var unchanged = balances.balance(person.id());
        assertThat(unchanged.charged()).isEqualTo(500);
        assertThat(unchanged.charges()).singleElement().satisfies(item -> assertThat(item.allocated()).isEqualTo(200));
        assertThat(unchanged.payments()).containsExactly(payment);

        payments.cancelAllocation(payment.id(), payment.allocations().getFirst().id());
        mvc.perform(delete("/api/charges/{id}", charge.id()).header("Authorization", ADMIN))
                .andExpect(status().isNoContent());
        var balance = balances.balance(person.id());
        assertThat(balance.charges()).isEmpty();
        assertThat(balance.paid()).isEqualTo(500);
        assertThat(balance.payments()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(payment.id());
            assertThat(item.allocations()).isEmpty();
        });
    }

    @Test
    void deletionRequiresAdministratorAndUnknownChargeReturnsNotFound() throws Exception {
        var person = people.createPerson(new PersonRequest("Проверка доступа", null, null));
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 500, DATE, "Ошибка", null));
        accounts.createAccount(new CreateUserRequest("charge-viewer", "viewer-test-password", UserRole.READ_ONLY));
        String viewer = "Basic " + Base64.getEncoder().encodeToString("charge-viewer:viewer-test-password".getBytes(StandardCharsets.UTF_8));

        mvc.perform(delete("/api/charges/{id}", charge.id())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/charges/{id}", charge.id()).header("Authorization", viewer)).andExpect(status().isForbidden());
        assertThat(balances.balance(person.id()).charges()).hasSize(1);
        mvc.perform(delete("/api/charges/{id}", Long.MAX_VALUE).header("Authorization", ADMIN)).andExpect(status().isNotFound());
    }
}
