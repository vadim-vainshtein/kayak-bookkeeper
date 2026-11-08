package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.ChargeEditRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-allocation-concurrency;MODE=PostgreSQL")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentAllocationConcurrencyTest {
    @Autowired BalanceService balanceService;
    @Autowired ChargeService chargeService;
    @Autowired PaymentService paymentService;
    @Autowired PersonService personService;

    @Test
    void simultaneousAllocationsCannotOverallocateTheSamePaymentOrCharge() throws Exception {
        PersonView person = personService.createPerson(new PersonRequest("Concurrent", null, null));
        ChargeView charge = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 100, LocalDate.of(2026, 11, 1), "Charge", null));
        PaymentView payment = paymentService.createPayment(new PaymentRequest(person.id(), 100, LocalDate.of(2026, 11, 1), PaymentMethod.CASH, null));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> allocateAfterStart(start, payment.id(), charge.id())),
                    executor.submit(() -> allocateAfterStart(start, payment.id(), charge.id())));
            start.countDown();
            assertThat(results.stream().map(this::resultOf).toList()).containsExactlyInAnyOrder(true, false);
        }
        ChargeView stored = balanceService.balance(person.id()).charges().stream().filter(candidate -> candidate.id().equals(charge.id())).findFirst().orElseThrow();
        assertThat(stored.allocated()).isEqualTo(75);
    }

    @Test
    void concurrentEditCannotReduceChargeBelowAnAllocation() throws Exception {
        PersonView person = personService.createPerson(new PersonRequest("Concurrent edit", null, null));
        ChargeView charge = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 100, LocalDate.of(2026, 11, 1), "Charge", null));
        PaymentView payment = paymentService.createPayment(new PaymentRequest(person.id(), 100, LocalDate.of(2026, 11, 1), PaymentMethod.CASH, null));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> allocateAfterStart(start, payment.id(), charge.id())),
                    executor.submit(() -> editAfterStart(start, charge.id())));
            start.countDown();
            assertThat(results.stream().map(this::resultOf).toList()).containsExactlyInAnyOrder(true, false);
        }
        ChargeView stored = balanceService.balance(person.id()).charges().stream().filter(candidate -> candidate.id().equals(charge.id())).findFirst().orElseThrow();
        assertThat(stored.amount()).isGreaterThanOrEqualTo(stored.allocated());
    }

    private boolean allocateAfterStart(CountDownLatch start, long paymentId, long chargeId) throws InterruptedException {
        start.await();
        try {
            paymentService.allocatePayment(paymentId, new AllocationRequest(chargeId, 75));
            return true;
        } catch (DomainException expected) {
            return false;
        }
    }

    private boolean editAfterStart(CountDownLatch start, long chargeId) throws InterruptedException {
        start.await();
        try {
            chargeService.editCharge(chargeId, new ChargeEditRequest(50, LocalDate.of(2026, 11, 2), "Reduced"));
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
