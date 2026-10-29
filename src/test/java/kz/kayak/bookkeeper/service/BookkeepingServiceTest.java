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
    @Test void usesTariffEffectiveOnEachTrainingDateAndRoundsPoolPerTraining() {
        LocalDate d=LocalDate.of(2026,11,10); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,30_000,d,d)); service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL,35_000,d.plusWeeks(1),null)); Api.SubscriptionView s=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(d.atTime(19,0),d.plusWeeks(1).atTime(19,0)),8));
        for(int i=0;i<7;i++){Api.PersonView p=service.createPerson(new Api.PersonRequest("P"+i,null,null));service.addMember(s.id(),new Api.MemberRequest(p.id(),false,false,false));} Api.SubscriptionView finalized=service.finalizeSubscription(s.id()); assertThat(finalized.poolRentalAmount()).isEqualTo(65_000); assertThat(finalized.members()).allSatisfy(m->assertThat(m.poolShare()).isEqualTo(9_286));
    }
    @Test void manualChargeCanBeEditedButNotBelowAllocated() {
        Api.PersonView p=service.createPerson(new Api.PersonRequest("Manual",null,null)); Api.ChargeView c=service.createManualCharge(new Api.ManualChargeRequest(p.id(),5_000,LocalDate.of(2026,11,1),"Correction",null)); service.editCharge(c.id(),new Api.ChargeEditRequest(6_000,LocalDate.of(2026,11,2),"Updated")); assertThat(service.balance(p.id()).charged()).isEqualTo(6_000); Api.PaymentView pay=service.createPayment(new Api.PaymentRequest(p.id(),4_000,LocalDate.of(2026,11,2),PaymentMethod.CASH,null)); service.allocatePayment(pay.id(),new Api.AllocationRequest(c.id(),4_000)); assertThatThrownBy(()->service.editCharge(c.id(),new Api.ChargeEditRequest(3_999,LocalDate.of(2026,11,2),"No"))).isInstanceOf(DomainException.class);
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
