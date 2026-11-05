package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.PaymentWithAllocationRequest;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No test transaction: these assertions observe the services' committed state after a failure. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:transaction-boundaries;MODE=PostgreSQL")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServiceTransactionBoundaryTest {
    @Autowired PersonService people;
    @Autowired PricingRateService rates;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionQueryService subscriptionQueries;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired ChargeService charges;
    @Autowired PaymentService payments;
    @Autowired BalanceService balances;

    @Test
    void failedFinalizationRollsBackEarlierMemberChargesAndStatus() {
        LocalDate date = LocalDate.of(2031, 1, 8);
        rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, date, date));
        PersonView first = person("First without equipment");
        PersonView second = person("Second needs a missing rate");
        SubscriptionView draft = subscription(date);
        subscriptions.addMember(draft.id(), new MemberRequest(first.id(), false, false, false));
        subscriptions.addMember(draft.id(), new MemberRequest(second.id(), true, false, false));

        assertThatThrownBy(() -> finalization.finalizeSubscription(draft.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("SKIRT_RENTAL");

        SubscriptionView stored = subscriptionQueries.getSubscription(draft.id());
        assertThat(stored.status()).isEqualTo(SubscriptionStatus.DRAFT);
        assertThat(stored.poolRentalAmount()).isNull();
        assertThat(stored.members()).allSatisfy(member -> assertThat(member.poolShare()).isNull());
        assertThat(balances.balance(first.id()).charges()).isEmpty();
        assertThat(balances.balance(second.id()).charges()).isEmpty();
    }


    @Test
    void invalidCombinedPaymentDoesNotLeaveAnUnallocatedPayment() {
        PersonView person = person("Atomic payment");
        LocalDate date = LocalDate.of(2033, 1, 8);
        ChargeView charge = charges.createManualCharge(
                new ManualChargeRequest(person.id(), 100, date, "Charge", null));

        assertThatThrownBy(() -> payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(
                person.id(), 50, date, PaymentMethod.CASH, null, charge.id(), 75L)))
                .isInstanceOf(DomainException.class)
                .hasMessage("Allocation exceeds payment amount");

        assertThat(balances.balance(person.id()).payments()).isEmpty();
        assertThat(balances.balance(person.id()).charges()).singleElement()
                .satisfies(stored -> assertThat(stored.allocated()).isZero());
    }

    private PersonView person(String name) {
        return people.createPerson(new PersonRequest(name, null, null));
    }

    private SubscriptionView subscription(LocalDate date) {
        return subscriptions.createExplicitSubscription(
                new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 2));
    }
}
