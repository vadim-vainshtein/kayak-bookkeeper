package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.RecurringSubscriptionWithMembersRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionMetadataUpdateRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class SubscriptionLifecycleServiceTest {
    @Autowired PersonService personService;
    @Autowired PricingRateService pricingRateService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired SubscriptionQueryService subscriptionQueryService;
    @Autowired SubscriptionFinalizationService subscriptionFinalizationService;
    @Autowired BalanceService balanceService;

    @Test
    void perTrainingChargesAreSummedAndBalanceUsesThem() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, d, null));
        pricingRateService.createRate(new RateRequest(RateCode.SKIRT_RENTAL, 1_500, d, null));
        SubscriptionView s = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0), d.plusWeeks(1).atTime(19, 0)), 2));
        PersonView a = personService.createPerson(new PersonRequest("A", null, null));
        PersonView b = personService.createPerson(new PersonRequest("B", null, null));
        subscriptionService.addMember(s.id(), new MemberRequest(a.id(), true, false, false));
        subscriptionService.addMember(s.id(), new MemberRequest(b.id(), false, false, false));
        SubscriptionView finalized = subscriptionFinalizationService.finalizeSubscription(s.id());
        assertThat(finalized.members()).filteredOn(m -> m.personId().equals(a.id())).singleElement().satisfies(m -> {
            assertThat(m.poolShare()).isEqualTo(30_000);
            assertThat(m.skirtFee()).isEqualTo(3_000);
        });
        assertThat(balanceService.balance(a.id()).charged()).isEqualTo(33_000);
    }

    @Test
    void finalizedSubscriptionMetadataCanBeEditedWithoutChangingCharges() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, d, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0)), 1,"До","Исходное"));
        PersonView person = personService.createPerson(new PersonRequest("Участник", null, null));
        subscriptionService.addMember(subscription.id(), new MemberRequest(person.id(), false, false, false));
        SubscriptionView finalized = subscriptionFinalizationService.finalizeSubscription(subscription.id());
        SubscriptionView updated = subscriptionService.updateSubscriptionMetadata(subscription.id(), new SubscriptionMetadataUpdateRequest("После","Обновлённое"));
        assertThat(updated.status()).isEqualTo(SubscriptionStatus.FINALIZED);
        assertThat(updated.title()).isEqualTo("После");
        assertThat(updated.description()).isEqualTo("Обновлённое");
        assertThat(updated.capacity()).isEqualTo(1);
        assertThat(updated.sessions()).hasSize(1);
        assertThat(balanceService.balance(person.id()).charged()).isEqualTo(30_000);
        assertThat(updated.poolRentalAmount()).isEqualTo(finalized.poolRentalAmount());
    }

    @Test
    void createsRecurringSubscriptionAndItsMembersAtomically() {
        PersonView first = personService.createPerson(new PersonRequest("Первый", null, null));
        PersonView second = personService.createPerson(new PersonRequest("Второй", null, null));
        RecurringSubscriptionWithMembersRequest request = new RecurringSubscriptionWithMembersRequest(DayOfWeek.TUESDAY, LocalTime.of(19, 0), LocalDate.of(2026, 11, 10), 2, 2,"Ноябрь", null, List.of(new MemberRequest(first.id(), true, false, false), new MemberRequest(second.id(), false, true, true)));
        SubscriptionView subscription = subscriptionService.createRecurringSubscriptionWithMembers(request);
        assertThat(subscription.sessions()).hasSize(2);
        assertThat(subscription.members()).hasSize(2);
        assertThat(subscription.members()).anySatisfy(member -> {
            assertThat(member.personId()).isEqualTo(first.id());
            assertThat(member.usesSkirt()).isTrue();
        });
        assertThatThrownBy(() -> subscriptionService.createRecurringSubscriptionWithMembers(new RecurringSubscriptionWithMembersRequest(DayOfWeek.TUESDAY, LocalTime.NOON, LocalDate.of(2026, 11, 10), 1, 1,"Не создать", null, List.of(new MemberRequest(first.id(), false, false, false), new MemberRequest(second.id(), false, false, false))))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> subscriptionService.createRecurringSubscriptionWithMembers(new RecurringSubscriptionWithMembersRequest(DayOfWeek.TUESDAY, LocalTime.NOON, LocalDate.of(2026, 11, 10), 1, 1,"Пустой", null, List.of()))).isInstanceOf(DomainException.class);
        assertThat(subscriptionQueryService.listSubscriptions()).extracting(SubscriptionView::title).doesNotContain("Не создать");
    }

    @Test
    void usesTariffEffectiveOnEachTrainingDateAndRoundsPoolPerTraining() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, d, d));
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 35_000, d.plusWeeks(1), null));
        SubscriptionView s = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0), d.plusWeeks(1).atTime(19, 0)), 8));
        for(int i = 0;
        i<7;
        i++){
            PersonView p = personService.createPerson(new PersonRequest("P"+i, null, null));
            subscriptionService.addMember(s.id(), new MemberRequest(p.id(), false, false, false));
        }
        SubscriptionView finalized = subscriptionFinalizationService.finalizeSubscription(s.id());
        assertThat(finalized.poolRentalAmount()).isEqualTo(65_000);
        assertThat(finalized.members()).allSatisfy(m -> assertThat(m.poolShare()).isEqualTo(9_286));
    }

    @Test
    void finalizesWithZeroRateWithoutCreatingZeroAmountCharges() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 0, d, null));
        SubscriptionView s = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0)), 1));
        PersonView p = personService.createPerson(new PersonRequest("Free", null, null));
        subscriptionService.addMember(s.id(), new MemberRequest(p.id(), false, false, false));
        SubscriptionView finalized = subscriptionFinalizationService.finalizeSubscription(s.id());
        assertThat(finalized.poolRentalAmount()).isZero();
        assertThat(finalized.members()).singleElement().satisfies(member -> assertThat(member.poolShare()).isZero());
        assertThat(balanceService.balance(p.id()).charges()).isEmpty();
    }
}
