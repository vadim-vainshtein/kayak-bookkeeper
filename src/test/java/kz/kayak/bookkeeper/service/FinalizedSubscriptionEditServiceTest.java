package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionEditPreviewView;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.SessionView;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class FinalizedSubscriptionEditServiceTest {
    @Autowired PersonService personService;
    @Autowired PricingRateService pricingRateService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired SubscriptionFinalizationService subscriptionFinalizationService;
    @Autowired FinalizedSubscriptionEditService finalizedSubscriptionEditService;
    @Autowired PaymentService paymentService;
    @Autowired BalanceService balanceService;

    @Test
    void finalizedSubscriptionEditWarnsBeforeChangingAllocatedChargeAndKeepsItsAllocation() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, d, null));
        PersonView person = personService.createPerson(new PersonRequest("Оплативший", null, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0)), 1));
        subscriptionService.addMember(subscription.id(), new MemberRequest(person.id(), false, false, false));
        subscriptionFinalizationService.finalizeSubscription(subscription.id());
        ChargeView charge = balanceService.balance(person.id()).charges().getFirst();
        PaymentView payment = paymentService.createPayment(new PaymentRequest(person.id(), 80, d, PaymentMethod.CASH, null));
        paymentService.allocatePayment(payment.id(), new AllocationRequest(charge.id(), 80));
        FinalizedSubscriptionUpdateRequest change = new FinalizedSubscriptionUpdateRequest(List.of(d.atTime(19, 0), d.plusWeeks(1).atTime(19, 0)), List.of(new MemberRequest(person.id(), false, false, false)), 1,"Изменённый", null, false);
        FinalizedSubscriptionEditPreviewView preview = finalizedSubscriptionEditService.previewFinalizedSubscriptionEdit(subscription.id(), change);
        assertThat(preview.requiresConfirmation()).isTrue();
        assertThat(preview.allocatedPayments()).singleElement().satisfies(warning -> {
            assertThat(warning.paymentId()).isEqualTo(payment.id());
            assertThat(warning.allocatedAmount()).isEqualTo(80);
            assertThat(warning.chargeId()).isEqualTo(charge.id());
        });
        assertThatThrownBy(() -> finalizedSubscriptionEditService.updateFinalizedSubscription(subscription.id(), change)).isInstanceOf(DomainException.class);
        SubscriptionView updated = finalizedSubscriptionEditService.updateFinalizedSubscription(subscription.id(), new FinalizedSubscriptionUpdateRequest(change.startsAt(), change.members(), change.capacity(), change.title(), change.description(), true));
        assertThat(updated.sessions()).hasSize(2);
        assertThat(updated.title()).isEqualTo("Изменённый");
        assertThat(balanceService.balance(person.id()).charges()).singleElement().satisfies(updatedCharge -> {
            assertThat(updatedCharge.id()).isEqualTo(charge.id());
            assertThat(updatedCharge.amount()).isEqualTo(200);
            assertThat(updatedCharge.allocated()).isEqualTo(80);
        });
    }

    @Test
    void finalizedSubscriptionEditDoesNotRequireConfirmationWhenNothingAllocatedIsChanged() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, d, null));
        PersonView person = personService.createPerson(new PersonRequest("Без оплаты", null, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0)), 1));
        subscriptionService.addMember(subscription.id(), new MemberRequest(person.id(), false, false, false));
        subscriptionFinalizationService.finalizeSubscription(subscription.id());
        FinalizedSubscriptionUpdateRequest change = new FinalizedSubscriptionUpdateRequest(List.of(d.plusWeeks(1).atTime(19, 0)), List.of(new MemberRequest(person.id(), false, false, false)), 1,"Новое название", null, false);
        assertThat(finalizedSubscriptionEditService.previewFinalizedSubscriptionEdit(subscription.id(), change).requiresConfirmation()).isFalse();
        assertThat(finalizedSubscriptionEditService.updateFinalizedSubscription(subscription.id(), change).sessions()).extracting(SessionView::startsAt).containsExactly(d.plusWeeks(1).atTime(19, 0));
    }
}
