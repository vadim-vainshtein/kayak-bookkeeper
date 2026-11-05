package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BalanceServiceTest {
    @Autowired PersonService people;
    @Autowired ChargeService charges;
    @Autowired PaymentService payments;
    @Autowired BalanceService balances;
    @Autowired SubscriptionRepository subscriptions;
    private final LocalDate date = LocalDate.of(2026, 11, 1);

    @Test
    void splitPaymentsAndMultipleChargesReconcileWithFreeMoneyAndSeparatePeople() {
        var person = people.createPerson(new PersonRequest("Иван", null, null));
        var other = people.createPerson(new PersonRequest("Иван", null, null));
        var first = subscriptions.save(new Subscription(8, "Ноябрь", null));
        var second = subscriptions.save(new Subscription(8, null, null));
        var charge = charge(person.id(), 18_000, first.getId());
        var extraCharge = charge(person.id(), 2_000, first.getId());
        var secondCharge = charge(person.id(), 8_000, second.getId());
        var unassigned = charge(person.id(), 2_000, null);
        var payment = payments.createPayment(new PaymentRequest(person.id(), 25_000, date, PaymentMethod.CASH, null));
        payments.allocatePayment(payment.id(), new AllocationRequest(charge.id(), 13_000));
        payments.allocatePayment(payment.id(), new AllocationRequest(extraCharge.id(), 2_000));
        payments.allocatePayment(payment.id(), new AllocationRequest(secondCharge.id(), 6_000));
        payments.allocatePayment(payment.id(), new AllocationRequest(unassigned.id(), 1_000));
        var otherCharge = charge(other.id(), 500, first.getId());
        payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(other.id(), 500, date, PaymentMethod.CASH, null, otherCharge.id(), 500L));

        var row = summary(person.id());
        assertThat(row.charged()).isEqualTo(30_000);
        assertThat(row.paid()).isEqualTo(25_000);
        assertThat(row.unallocated()).isEqualTo(3_000);
        assertThat(row.balance()).isEqualTo(5_000);
        assertThat(row.subscriptionBalances()).containsExactly(
                new PersonSubscriptionBalanceView(first.getId(), "Ноябрь", 20_000, 15_000, 5_000),
                new PersonSubscriptionBalanceView(second.getId(), null, 8_000, 6_000, 2_000),
                new PersonSubscriptionBalanceView(null, "Без абонемента", 2_000, 1_000, 1_000));
        assertThat(row.subscriptionBalances().stream().mapToLong(PersonSubscriptionBalanceView::charged).sum()).isEqualTo(row.charged());
        assertThat(row.subscriptionBalances().stream().mapToLong(PersonSubscriptionBalanceView::allocated).sum() + row.unallocated()).isEqualTo(row.paid());
        assertThat(row.subscriptionBalances().stream().mapToLong(PersonSubscriptionBalanceView::balance).sum() - row.unallocated()).isEqualTo(row.balance());
        assertThat(summary(other.id()).subscriptionBalances()).containsExactly(new PersonSubscriptionBalanceView(first.getId(), "Ноябрь", 500, 500, 0));
    }

    @Test
    void movingChargeAndCancellingAllocationUpdatesGroupsWithoutAssigningFreeMoney() {
        var person = people.createPerson(new PersonRequest("Перенос начисления", null, null));
        var subscription = subscriptions.save(new Subscription(8, "Ноябрь", null));
        var charge = charge(person.id(), 100, subscription.getId());
        var payment = payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(person.id(), 150, date, PaymentMethod.CASH, null, charge.id(), 75L));
        charges.editCharge(charge.id(), new ChargeEditRequest(100, date, "Перенос", null, true));
        assertThat(summary(person.id()).subscriptionBalances()).containsExactly(new PersonSubscriptionBalanceView(null, "Без абонемента", 100, 75, 25));
        payments.cancelAllocation(payment.id(), payment.allocations().getFirst().id());
        assertThat(summary(person.id()).subscriptionBalances()).containsExactly(new PersonSubscriptionBalanceView(null, "Без абонемента", 100, 0, 100));
        assertThat(summary(person.id()).unallocated()).isEqualTo(150);
    }

    @Test
    void keepsEmptyKayakersAndPaymentOnlyCreditWithoutInventingSubscriptionRows() {
        var empty = people.createPerson(new PersonRequest("Без операций", null, null));
        var credit = people.createPerson(new PersonRequest("Аванс", null, null));
        payments.createPayment(new PaymentRequest(credit.id(), 400, date, PaymentMethod.TRANSFER, null));
        assertThat(summary(empty.id()).subscriptionBalances()).isEmpty();
        assertThat(summary(empty.id()).balance()).isZero();
        assertThat(summary(credit.id()).subscriptionBalances()).isEmpty();
        assertThat(summary(credit.id()).unallocated()).isEqualTo(400);
        assertThat(summary(credit.id()).balance()).isEqualTo(-400);
    }

    private ChargeView charge(long personId, long amount, Long subscriptionId) {
        return charges.createManualCharge(new ManualChargeRequest(personId, amount, date, "Начисление", subscriptionId));
    }

    private PersonBalanceSummaryView summary(long personId) {
        return balances.listPersonBalances().stream().filter(row -> row.personId() == personId).findFirst().orElseThrow();
    }
}
