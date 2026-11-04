package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PaymentWithAllocationRequest;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentServiceTest {
    @Autowired PersonService personService;
    @Autowired ChargeService chargeService;
    @Autowired PaymentService paymentService;
    @Autowired BalanceService balanceService;

    @Test
    void paymentAndAllocationAreReturnedTogetherAndRollbackWhenAllocationIsInvalid() {
        PersonView person = personService.createPerson(new PersonRequest("Payment", null, null));
        ChargeView charge = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 5_000, LocalDate.of(2026, 11, 1),"Correction", null));
        PaymentView payment = paymentService.createPaymentWithAllocation(new PaymentWithAllocationRequest(person.id(), 4_000, LocalDate.of(2026, 11, 2), PaymentMethod.TRANSFER,"bank", charge.id(), 4_000L));
        assertThat(payment.allocations()).singleElement().satisfies(allocation -> {
            assertThat(allocation.chargeId()).isEqualTo(charge.id());
            assertThat(allocation.amount()).isEqualTo(4_000);
        });
        assertThat(balanceService.balance(person.id()).payments()).singleElement().satisfies(stored -> assertThat(stored.allocations()).hasSize(1));
        assertThatThrownBy(() -> paymentService.createPaymentWithAllocation(new PaymentWithAllocationRequest(person.id(), 1_000, LocalDate.of(2026, 11, 3), PaymentMethod.CASH, null, charge.id(), 2_000L))).isInstanceOf(DomainException.class);
        assertThat(balanceService.balance(person.id()).payments()).hasSize(1);
    }
}
