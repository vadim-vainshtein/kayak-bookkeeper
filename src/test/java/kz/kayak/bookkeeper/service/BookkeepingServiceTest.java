package kz.kayak.bookkeeper.service;

import kz.kayak.bookkeeper.api.Api;
import kz.kayak.bookkeeper.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Transactional
class BookkeepingServiceTest {
    @Autowired BookkeepingService service;
    @Test void perTrainingChargesAreSummedAndBalanceUsesThem() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,30_000,d,null)); service.createRate(new Api.RateRequest(RateCode.SKIRT_RENTAL,1_500,d,null));
        Api.SubscriptionView s=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0),d.plusWeeks(1).atTime(19,0)),2)); Api.PersonView a=service.createPerson(new Api.PersonRequest("A",null,null)); Api.PersonView b=service.createPerson(new Api.PersonRequest("B",null,null)); service.addMember(s.id(),new Api.MemberRequest(a.id(),true,false,false)); service.addMember(s.id(),new Api.MemberRequest(b.id(),false,false,false));
        Api.SubscriptionView finalized=service.finalizeSubscription(s.id()); assertThat(finalized.members()).filteredOn(m->m.personId().equals(a.id())).singleElement().satisfies(m->{assertThat(m.poolShare()).isEqualTo(30_000);assertThat(m.skirtFee()).isEqualTo(3_000);}); assertThat(service.balance(a.id()).charged()).isEqualTo(33_000);
    }
    @Test void finalizedSubscriptionMetadataCanBeEditedWithoutChangingCharges() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,30_000,d,null)); Api.SubscriptionView subscription=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0)),1,"До","Исходное")); Api.PersonView person=service.createPerson(new Api.PersonRequest("Участник",null,null)); service.addMember(subscription.id(),new Api.MemberRequest(person.id(),false,false,false)); Api.SubscriptionView finalized=service.finalizeSubscription(subscription.id());
        Api.SubscriptionView updated=service.updateSubscriptionMetadata(subscription.id(),new Api.SubscriptionMetadataUpdateRequest("После","Обновлённое"));
        assertThat(updated.status()).isEqualTo(SubscriptionStatus.FINALIZED); assertThat(updated.title()).isEqualTo("После"); assertThat(updated.description()).isEqualTo("Обновлённое"); assertThat(updated.capacity()).isEqualTo(1); assertThat(updated.sessions()).hasSize(1); assertThat(service.balance(person.id()).charged()).isEqualTo(30_000); assertThat(updated.poolRentalAmount()).isEqualTo(finalized.poolRentalAmount());
    }
    @Test void createsRecurringSubscriptionAndItsMembersAtomically() {
        Api.PersonView first=service.createPerson(new Api.PersonRequest("Первый",null,null)); Api.PersonView second=service.createPerson(new Api.PersonRequest("Второй",null,null));
        Api.RecurringSubscriptionWithMembersRequest request=new Api.RecurringSubscriptionWithMembersRequest(DayOfWeek.TUESDAY,LocalTime.of(19,0),LocalDate.of(2026,11,10),2,2,"Ноябрь",null,List.of(new Api.MemberRequest(first.id(),true,false,false),new Api.MemberRequest(second.id(),false,true,true)));
        Api.SubscriptionView subscription=service.createRecurringSubscriptionWithMembers(request);
        assertThat(subscription.sessions()).hasSize(2); assertThat(subscription.members()).hasSize(2); assertThat(subscription.members()).anySatisfy(member -> { assertThat(member.personId()).isEqualTo(first.id()); assertThat(member.usesSkirt()).isTrue(); });
        assertThatThrownBy(() -> service.createRecurringSubscriptionWithMembers(new Api.RecurringSubscriptionWithMembersRequest(DayOfWeek.TUESDAY,LocalTime.NOON,LocalDate.of(2026,11,10),1,1,"Не создать",null,List.of(new Api.MemberRequest(first.id(),false,false,false),new Api.MemberRequest(second.id(),false,false,false))))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.createRecurringSubscriptionWithMembers(new Api.RecurringSubscriptionWithMembersRequest(DayOfWeek.TUESDAY,LocalTime.NOON,LocalDate.of(2026,11,10),1,1,"Пустой",null,List.of()))).isInstanceOf(DomainException.class);
        assertThat(service.listSubscriptions()).extracting(Api.SubscriptionView::title).doesNotContain("Не создать");
    }
    @Test void usesTariffEffectiveOnEachTrainingDateAndRoundsPoolPerTraining() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,30_000,d,d)); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,35_000,d.plusWeeks(1),null)); Api.SubscriptionView s=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0),d.plusWeeks(1).atTime(19,0)),8));
        for(int i=0;i<7;i++){Api.PersonView p=service.createPerson(new Api.PersonRequest("P"+i,null,null));service.addMember(s.id(),new Api.MemberRequest(p.id(),false,false,false));} Api.SubscriptionView finalized=service.finalizeSubscription(s.id()); assertThat(finalized.poolRentalAmount()).isEqualTo(65_000); assertThat(finalized.members()).allSatisfy(m->assertThat(m.poolShare()).isEqualTo(9_286));
    }
    @Test void finalizesWithZeroRateWithoutCreatingZeroAmountCharges() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,0,d,null)); Api.SubscriptionView s=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0)),1)); Api.PersonView p=service.createPerson(new Api.PersonRequest("Free",null,null)); service.addMember(s.id(),new Api.MemberRequest(p.id(),false,false,false));
        Api.SubscriptionView finalized=service.finalizeSubscription(s.id());
        assertThat(finalized.poolRentalAmount()).isZero(); assertThat(finalized.members()).singleElement().satisfies(member->assertThat(member.poolShare()).isZero()); assertThat(service.balance(p.id()).charges()).isEmpty();
    }
    @Test void finalizedSubscriptionEditWarnsBeforeChangingAllocatedChargeAndKeepsItsAllocation() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,100,d,null)); Api.PersonView person=service.createPerson(new Api.PersonRequest("Оплативший",null,null));
        Api.SubscriptionView subscription=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0)),1)); service.addMember(subscription.id(),new Api.MemberRequest(person.id(),false,false,false)); service.finalizeSubscription(subscription.id());
        Api.ChargeView charge=service.balance(person.id()).charges().getFirst(); Api.PaymentView payment=service.createPayment(new Api.PaymentRequest(person.id(),80,d,PaymentMethod.CASH,null)); service.allocatePayment(payment.id(),new Api.AllocationRequest(charge.id(),80));
        Api.FinalizedSubscriptionUpdateRequest change=new Api.FinalizedSubscriptionUpdateRequest(List.of(d.atTime(19,0),d.plusWeeks(1).atTime(19,0)),List.of(new Api.MemberRequest(person.id(),false,false,false)),1,"Изменённый",null,false);
        Api.FinalizedSubscriptionEditPreviewView preview=service.previewFinalizedSubscriptionEdit(subscription.id(),change);
        assertThat(preview.requiresConfirmation()).isTrue(); assertThat(preview.allocatedPayments()).singleElement().satisfies(warning -> { assertThat(warning.paymentId()).isEqualTo(payment.id()); assertThat(warning.allocatedAmount()).isEqualTo(80); assertThat(warning.chargeId()).isEqualTo(charge.id()); });
        assertThatThrownBy(() -> service.updateFinalizedSubscription(subscription.id(),change)).isInstanceOf(DomainException.class);
        Api.SubscriptionView updated=service.updateFinalizedSubscription(subscription.id(),new Api.FinalizedSubscriptionUpdateRequest(change.startsAt(),change.members(),change.capacity(),change.title(),change.description(),true));
        assertThat(updated.sessions()).hasSize(2); assertThat(updated.title()).isEqualTo("Изменённый"); assertThat(service.balance(person.id()).charges()).singleElement().satisfies(updatedCharge -> { assertThat(updatedCharge.id()).isEqualTo(charge.id()); assertThat(updatedCharge.amount()).isEqualTo(200); assertThat(updatedCharge.allocated()).isEqualTo(80); });
    }
    @Test void finalizedSubscriptionEditDoesNotRequireConfirmationWhenNothingAllocatedIsChanged() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,100,d,null)); Api.PersonView person=service.createPerson(new Api.PersonRequest("Без оплаты",null,null));
        Api.SubscriptionView subscription=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0)),1)); service.addMember(subscription.id(),new Api.MemberRequest(person.id(),false,false,false)); service.finalizeSubscription(subscription.id());
        Api.FinalizedSubscriptionUpdateRequest change=new Api.FinalizedSubscriptionUpdateRequest(List.of(d.plusWeeks(1).atTime(19,0)),List.of(new Api.MemberRequest(person.id(),false,false,false)),1,"Новое название",null,false);
        assertThat(service.previewFinalizedSubscriptionEdit(subscription.id(),change).requiresConfirmation()).isFalse();
        assertThat(service.updateFinalizedSubscription(subscription.id(),change).sessions()).extracting(Api.SessionView::startsAt).containsExactly(d.plusWeeks(1).atTime(19,0));
    }
    @Test void manualChargeCanBeEditedButNotBelowAllocated() {
        Api.PersonView p=service.createPerson(new Api.PersonRequest("Manual",null,null)); Api.ChargeView c=service.createManualCharge(new Api.ManualChargeRequest(p.id(),5_000,LocalDate.of(2026,11,1),"Correction",null)); service.editCharge(c.id(),new Api.ChargeEditRequest(6_000,LocalDate.of(2026,11,2),"Updated")); assertThat(service.balance(p.id()).charged()).isEqualTo(6_000); Api.PaymentView pay=service.createPayment(new Api.PaymentRequest(p.id(),4_000,LocalDate.of(2026,11,2),PaymentMethod.CASH,null)); service.allocatePayment(pay.id(),new Api.AllocationRequest(c.id(),4_000)); assertThatThrownBy(()->service.editCharge(c.id(),new Api.ChargeEditRequest(3_999,LocalDate.of(2026,11,2),"No"))).isInstanceOf(DomainException.class);
    }
    @Test void paymentAndAllocationAreReturnedTogetherAndRollbackWhenAllocationIsInvalid() {
        Api.PersonView person=service.createPerson(new Api.PersonRequest("Payment",null,null));
        Api.ChargeView charge=service.createManualCharge(new Api.ManualChargeRequest(person.id(),5_000,LocalDate.of(2026,11,1),"Correction",null));
        Api.PaymentView payment=service.createPaymentWithAllocation(new Api.PaymentWithAllocationRequest(person.id(),4_000,LocalDate.of(2026,11,2),PaymentMethod.TRANSFER,"bank",charge.id(),4_000L));
        assertThat(payment.allocations()).singleElement().satisfies(allocation -> { assertThat(allocation.chargeId()).isEqualTo(charge.id()); assertThat(allocation.amount()).isEqualTo(4_000); });
        assertThat(service.balance(person.id()).payments()).singleElement().satisfies(stored -> assertThat(stored.allocations()).hasSize(1));
        assertThatThrownBy(() -> service.createPaymentWithAllocation(new Api.PaymentWithAllocationRequest(person.id(),1_000,LocalDate.of(2026,11,3),PaymentMethod.CASH,null,charge.id(),2_000L))).isInstanceOf(DomainException.class);
        assertThat(service.balance(person.id()).payments()).hasSize(1);
    }
    @Test void editingAutomaticChargeSynchronizesSubscriptionMemberSnapshot() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,30_000,d,null)); Api.SubscriptionView s=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0)),1)); Api.PersonView p=service.createPerson(new Api.PersonRequest("Auto",null,null)); service.addMember(s.id(),new Api.MemberRequest(p.id(),false,false,false)); service.finalizeSubscription(s.id());
        Api.ChargeView pool=service.balance(p.id()).charges().stream().filter(c->c.type()==ChargeType.POOL_SHARE).findFirst().orElseThrow(); service.editCharge(pool.id(),new Api.ChargeEditRequest(31_000,d,"Manual pool adjustment"));
        assertThat(service.getSubscription(s.id()).members()).singleElement().satisfies(m->assertThat(m.poolShare()).isEqualTo(31_000)); assertThat(service.balance(p.id()).charges()).filteredOn(c->c.id().equals(pool.id())).singleElement().satisfies(c->assertThat(c.amount()).isEqualTo(31_000));
    }
    @Test void rosterAndDropInUseSubscriptionCapacity() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,30_000,d,null));service.createRate(new Api.RateRequest(RateCode.DROP_IN,5_000,d,null)); Api.SubscriptionView s=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0)),1)); Api.PersonView member=service.createPerson(new Api.PersonRequest("Member",null,null)); service.addMember(s.id(),new Api.MemberRequest(member.id(),false,false,false)); long session=service.finalizeSubscription(s.id()).sessions().getFirst().id(); service.markSubscriptionAttendance(session,new Api.AttendanceRequest(member.id(),AttendanceStatus.ABSENT)); Api.PersonView visitor=service.createPerson(new Api.PersonRequest("Visitor",null,null)); service.addDropIn(session,new Api.DropInRequest(visitor.id())); assertThat(service.roster(session).capacity()).isEqualTo(1); assertThat(service.balance(visitor.id()).charged()).isEqualTo(5_000);
    }
}
