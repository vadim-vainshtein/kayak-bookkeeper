package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.ChargeEditRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ChargeServiceTest {
    @Autowired PersonService personService;
    @Autowired PricingRateService pricingRateService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired SubscriptionQueryService subscriptionQueryService;
    @Autowired SubscriptionFinalizationService subscriptionFinalizationService;
    @Autowired ChargeService chargeService;
    @Autowired PaymentService paymentService;
    @Autowired BalanceService balanceService;

    @Test
    void manualChargeCanBeEditedButNotBelowAllocated() {
        PersonView p = personService.createPerson(new PersonRequest("Manual", null, null));
        ChargeView c = chargeService.createManualCharge(new ManualChargeRequest(p.id(), 5_000, LocalDate.of(2026, 11, 1),"Correction", null));
        chargeService.editCharge(c.id(), new ChargeEditRequest(6_000, LocalDate.of(2026, 11, 2),"Updated"));
        assertThat(balanceService.balance(p.id()).charged()).isEqualTo(6_000);
        PaymentView pay = paymentService.createPayment(new PaymentRequest(p.id(), 4_000, LocalDate.of(2026, 11, 2), PaymentMethod.CASH, null));
        paymentService.allocatePayment(pay.id(), new AllocationRequest(c.id(), 4_000));
        assertThatThrownBy(() -> chargeService.editCharge(c.id(), new ChargeEditRequest(3_999, LocalDate.of(2026, 11, 2),"No"))).isInstanceOf(DomainException.class);
    }

    @Test
    void manualChargeCanBeAttachedToAnySubscriptionWhenCreatedOrEdited() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        PersonView person = personService.createPerson(new PersonRequest("Не участник", null, null));
        SubscriptionView first = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 1,"Первый", null));
        SubscriptionView second = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.plusWeeks(1).atTime(19, 0)), 1,"Второй", null));
        ChargeView created = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 5_000, date,"Корректировка", first.id()));
        ChargeView edited = chargeService.editCharge(created.id(), new ChargeEditRequest(5_000, date,"Корректировка", second.id()));
        assertThat(created.subscriptionId()).isEqualTo(first.id());
        assertThat(edited.subscriptionId()).isEqualTo(second.id());
    }

    @Test
    void editingAutomaticChargeSynchronizesSubscriptionMemberSnapshot() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, d, null));
        SubscriptionView s = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0)), 1));
        PersonView p = personService.createPerson(new PersonRequest("Auto", null, null));
        subscriptionService.addMember(s.id(), new MemberRequest(p.id(), false, false, false));
        subscriptionFinalizationService.finalizeSubscription(s.id());
        ChargeView pool = balanceService.balance(p.id()).charges().stream().filter(c -> c.type()==ChargeType.POOL_SHARE).findFirst().orElseThrow();
        chargeService.editCharge(pool.id(), new ChargeEditRequest(31_000, d,"Manual pool adjustment"));
        assertThat(subscriptionQueryService.getSubscription(s.id()).members()).singleElement().satisfies(m -> assertThat(m.poolShare()).isEqualTo(31_000));
        assertThat(balanceService.balance(p.id()).charges()).filteredOn(c -> c.id().equals(pool.id())).singleElement().satisfies(c -> {
            assertThat(c.amount()).isEqualTo(31_000);
            assertThat(c.subscriptionId()).isEqualTo(s.id());
        });
    }
}
