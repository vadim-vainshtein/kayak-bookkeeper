package kz.kayak.bookkeeper.service;

import kz.kayak.bookkeeper.api.Api;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentAllocationConcurrencyTest {
    @Autowired BookkeepingService service;

    @Test
    void simultaneousAllocationsCannotOverallocateTheSamePaymentOrCharge() throws Exception {
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Concurrent", null, null));
        Api.ChargeView charge = service.createManualCharge(new Api.ManualChargeRequest(person.id(), 100, LocalDate.of(2026, 11, 1), "Charge", null));
        Api.PaymentView payment = service.createPayment(new Api.PaymentRequest(person.id(), 100, LocalDate.of(2026, 11, 1), PaymentMethod.CASH, null));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> allocateAfterStart(start, payment.id(), charge.id())),
                    executor.submit(() -> allocateAfterStart(start, payment.id(), charge.id())));
            start.countDown();
            assertThat(results.stream().map(this::resultOf).toList()).containsExactlyInAnyOrder(true, false);
        }
        Api.ChargeView stored = service.balance(person.id()).charges().stream().filter(candidate -> candidate.id().equals(charge.id())).findFirst().orElseThrow();
        assertThat(stored.allocated()).isEqualTo(75);
    }

    @Test
    void concurrentEditCannotReduceChargeBelowAnAllocation() throws Exception {
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Concurrent edit", null, null));
        Api.ChargeView charge = service.createManualCharge(new Api.ManualChargeRequest(person.id(), 100, LocalDate.of(2026, 11, 1), "Charge", null));
        Api.PaymentView payment = service.createPayment(new Api.PaymentRequest(person.id(), 100, LocalDate.of(2026, 11, 1), PaymentMethod.CASH, null));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> allocateAfterStart(start, payment.id(), charge.id())),
                    executor.submit(() -> editAfterStart(start, charge.id())));
            start.countDown();
            assertThat(results.stream().map(this::resultOf).toList()).containsExactlyInAnyOrder(true, false);
        }
        Api.ChargeView stored = service.balance(person.id()).charges().stream().filter(candidate -> candidate.id().equals(charge.id())).findFirst().orElseThrow();
        assertThat(stored.amount()).isGreaterThanOrEqualTo(stored.allocated());
    }

    private boolean allocateAfterStart(CountDownLatch start, long paymentId, long chargeId) throws InterruptedException {
        start.await();
        try {
            service.allocatePayment(paymentId, new Api.AllocationRequest(chargeId, 75));
            return true;
        } catch (DomainException expected) {
            return false;
        }
    }

    private boolean editAfterStart(CountDownLatch start, long chargeId) throws InterruptedException {
        start.await();
        try {
            service.editCharge(chargeId, new Api.ChargeEditRequest(50, LocalDate.of(2026, 11, 2), "Reduced"));
            return true;
        } catch (DomainException expected) {
            return false;
        }
    }

    private boolean resultOf(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent allocation did not complete", exception);
        }
    }
}
