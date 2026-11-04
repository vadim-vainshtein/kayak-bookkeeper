package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.AttendanceRequest;
import kz.kayak.bookkeeper.api.dto.AttendanceView;
import kz.kayak.bookkeeper.api.dto.DropInRequest;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
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
class AttendanceServiceTest {
    @Autowired PersonService personService;
    @Autowired PricingRateService pricingRateService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired SubscriptionFinalizationService subscriptionFinalizationService;
    @Autowired AttendanceService attendanceService;
    @Autowired BalanceService balanceService;

    @Test
    void rosterAndDropInUseSubscriptionCapacity() {
        LocalDate d = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, d, null));
        pricingRateService.createRate(new RateRequest(RateCode.DROP_IN, 5_000, d, null));
        SubscriptionView s = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(d.atTime(19, 0)), 1));
        PersonView member = personService.createPerson(new PersonRequest("Member", null, null));
        subscriptionService.addMember(s.id(), new MemberRequest(member.id(), false, false, false));
        long session = subscriptionFinalizationService.finalizeSubscription(s.id()).sessions().getFirst().id();
        attendanceService.markSubscriptionAttendance(session, new AttendanceRequest(member.id(), AttendanceStatus.ABSENT));
        PersonView visitor = personService.createPerson(new PersonRequest("Visitor", null, null));
        attendanceService.addDropIn(session, new DropInRequest(visitor.id()));
        assertThat(attendanceService.roster(session).capacity()).isEqualTo(1);
        assertThat(balanceService.balance(visitor.id()).charged()).isEqualTo(5_000);
    }

    @Test
    void rosterUsesPersonIdToOrderSameNamedKayakers() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, date, null));
        PersonView first = personService.createPerson(new PersonRequest("Алия", null, null));
        PersonView second = personService.createPerson(new PersonRequest("Алия", null, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 2));
        subscriptionService.addMember(subscription.id(), new MemberRequest(second.id(), false, false, false));
        subscriptionService.addMember(subscription.id(), new MemberRequest(first.id(), false, false, false));
        long sessionId = subscriptionFinalizationService.finalizeSubscription(subscription.id()).sessions().getFirst().id();
        assertThat(attendanceService.roster(sessionId).attendance()).extracting(AttendanceView::personId).containsExactly(first.id(), second.id());
    }
}
