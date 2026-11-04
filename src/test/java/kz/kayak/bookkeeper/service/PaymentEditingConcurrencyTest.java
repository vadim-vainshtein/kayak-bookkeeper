package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentEditingConcurrencyTest {
    @Autowired PersonService people;
    @Autowired ChargeService charges;
    @Autowired PaymentService payments;
    @Autowired BalanceService balances;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);

    @Test
    void amountReductionAndAllocationCannotLeaveAnOverallocatedPayment() throws Exception {
        var person = people.createPerson(new PersonRequest("Concurrent payment edit", null, null));
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 100, DATE, "Charge", null));
        var payment = payments.createPayment(new PaymentRequest(person.id(), 100, DATE, PaymentMethod.CASH, null));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = List.of(
                    executor.submit(() -> afterStart(start, () -> payments.editPaymentAmount(payment.id(), new PaymentAmountRequest(50)))),
                    executor.submit(() -> afterStart(start, () -> payments.allocatePayment(payment.id(), new AllocationRequest(charge.id(), 75)))));
            start.countDown();
            assertThat(List.of(result(results.getFirst()), result(results.getLast()))).containsExactlyInAnyOrder(true, false);
        }
        var stored = balances.balance(person.id()).payments().getFirst();
        assertThat(stored.amount()).isGreaterThanOrEqualTo(stored.allocations().stream().mapToLong(PaymentAllocationView::amount).sum());
    }

    @Test
    void cancellationAndNewAllocationKeepPaymentAndChargeTotalsConsistent() throws Exception {
        var person = people.createPerson(new PersonRequest("Concurrent cancellation", null, null));
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 100, DATE, "First", null));
        var otherCharge = charges.createManualCharge(new ManualChargeRequest(person.id(), 100, DATE, "Second", null));
        var payment = payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(
                person.id(), 100, DATE, PaymentMethod.CASH, null, charge.id(), 75L));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancel = executor.submit(() -> afterStart(start, () -> payments.cancelAllocation(payment.id(), payment.allocations().getFirst().id())));
            var allocate = executor.submit(() -> afterStart(start, () -> payments.allocatePayment(payment.id(), new AllocationRequest(otherCharge.id(), 75))));
            start.countDown();
            assertThat(result(cancel)).isTrue();
            result(allocate);
        }
        var balance = balances.balance(person.id());
        var stored = balance.payments().getFirst();
        assertThat(stored.amount()).isGreaterThanOrEqualTo(stored.allocations().stream().mapToLong(PaymentAllocationView::amount).sum());
        assertThat(balance.charges().stream().filter(item -> item.id().equals(charge.id())).findFirst().orElseThrow().allocated()).isZero();
        assertThat(balance.charges().stream().mapToLong(ChargeView::allocated).sum())
                .isEqualTo(stored.allocations().stream().mapToLong(PaymentAllocationView::amount).sum());
    }

    private boolean afterStart(CountDownLatch start, Runnable action) throws InterruptedException {
        start.await();
        try {
            action.run();
            return true;
        } catch (DomainException expected) {
            return false;
        }
    }

    private boolean result(Future<Boolean> result) throws Exception {
        return result.get(10, TimeUnit.SECONDS);
    }
}
