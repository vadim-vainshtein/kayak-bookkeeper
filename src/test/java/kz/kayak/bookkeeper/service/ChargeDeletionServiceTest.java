package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.ChargeType;
import kz.kayak.bookkeeper.domain.RateCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChargeDeletionServiceTest {
    @Autowired PersonService people;
    @Autowired PricingRateService rates;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired SubscriptionQueryService queries;
    @Autowired ChargeService charges;
    @Autowired BalanceService balances;

    @Test
    void deletingEachAutomaticFeeClearsOnlyItsMemberSnapshot() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, date, null));
        rates.createRate(new RateRequest(RateCode.SKIRT_RENTAL, 1_000, date, null));
        rates.createRate(new RateRequest(RateCode.PADDLE_RENTAL, 2_000, date, null));
        rates.createRate(new RateRequest(RateCode.KAYAK_RENTAL, 3_000, date, null));
        var person = people.createPerson(new PersonRequest("Автоматические начисления", null, null));
        var subscription = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 1));
        subscriptions.addMember(subscription.id(), new MemberRequest(person.id(), true, true, true));
        finalization.finalizeSubscription(subscription.id());
        var original = balances.balance(person.id()).charges();
        assertThat(original).hasSize(4);

        for (ChargeType type : List.of(ChargeType.POOL_SHARE, ChargeType.SKIRT_RENT, ChargeType.PADDLE_RENT, ChargeType.KAYAK_RENT)) {
            var charge = original.stream().filter(item -> item.type() == type).findFirst().orElseThrow();
            var before = queries.getSubscription(subscription.id()).members().getFirst();
            charges.deleteCharge(charge.id());
            var after = queries.getSubscription(subscription.id()).members().getFirst();
            assertThat(after.poolShare()).isEqualTo(type == ChargeType.POOL_SHARE ? 0L : before.poolShare());
            assertThat(after.skirtFee()).isEqualTo(type == ChargeType.SKIRT_RENT ? 0L : before.skirtFee());
            assertThat(after.paddleFee()).isEqualTo(type == ChargeType.PADDLE_RENT ? 0L : before.paddleFee());
            assertThat(after.kayakFee()).isEqualTo(type == ChargeType.KAYAK_RENT ? 0L : before.kayakFee());
            assertThat(balances.balance(person.id()).charges()).noneMatch(item -> item.id().equals(charge.id()));
        }
        assertThat(balances.balance(person.id()).charged()).isZero();
    }

    @Test
    void deletingLinkedManualChargePreservesAutomaticFeeSnapshot() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, date, null));
        var person = people.createPerson(new PersonRequest("Ручная корректировка", null, null));
        var subscription = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 1));
        subscriptions.addMember(subscription.id(), new MemberRequest(person.id(), false, false, false));
        finalization.finalizeSubscription(subscription.id());
        var charge = charges.createManualCharge(new ManualChargeRequest(person.id(), 500, date, "Ошибка", subscription.id()));
        charges.deleteCharge(charge.id());

        assertThat(queries.getSubscription(subscription.id()).members()).singleElement().satisfies(member -> assertThat(member.poolShare()).isEqualTo(30_000));
        assertThat(balances.balance(person.id()).charged()).isEqualTo(30_000);
    }
}
