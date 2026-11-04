package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.FinancialReportSummaryView;
import kz.kayak.bookkeeper.api.dto.FinancialReportView;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.MemberView;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PersonBalanceSummaryView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionFinancialReportView;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class FinancialReportServiceTest {
    @Autowired PersonService personService;
    @Autowired PricingRateService pricingRateService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired SubscriptionQueryService subscriptionQueryService;
    @Autowired SubscriptionFinalizationService subscriptionFinalizationService;
    @Autowired ChargeService chargeService;
    @Autowired PaymentService paymentService;
    @Autowired BalanceService balanceService;
    @Autowired FinancialReportService financialReportService;

    @Test
    void financialReportUsesAllTimeSubscriptionTotalsAndListsUnassignedCharges() {
        LocalDate date = LocalDate.now();
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, date, null));
        PersonView person = personService.createPerson(new PersonRequest("Два абонемента", null, null));
        SubscriptionView first = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(18, 0)), 1,"Первый", null));
        subscriptionService.addMember(first.id(), new MemberRequest(person.id(), false, false, false));
        subscriptionFinalizationService.finalizeSubscription(first.id());
        SubscriptionView second = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.plusWeeks(1).atTime(18, 0)), 1,"Второй", null));
        subscriptionService.addMember(second.id(), new MemberRequest(person.id(), false, false, false));
        subscriptionFinalizationService.finalizeSubscription(second.id());
        ChargeView firstCharge = balanceService.balance(person.id()).charges().stream().filter(charge -> charge.subscriptionId().equals(first.id())).findFirst().orElseThrow();
        ChargeView secondCharge = balanceService.balance(person.id()).charges().stream().filter(charge -> charge.subscriptionId().equals(second.id())).findFirst().orElseThrow();
        PaymentView payment = paymentService.createPayment(new PaymentRequest(person.id(), 100, date, PaymentMethod.CASH, null));
        paymentService.allocatePayment(payment.id(), new AllocationRequest(firstCharge.id(), 60));
        paymentService.allocatePayment(payment.id(), new AllocationRequest(secondCharge.id(), 40));
        ChargeView unassigned = chargeService.createManualCharge(new ManualChargeRequest(person.id(), 25, date.minusYears(2),"Корректировка", null));
        PaymentView unassignedPayment = paymentService.createPayment(new PaymentRequest(person.id(), 10, date, PaymentMethod.CASH, null));
        paymentService.allocatePayment(unassignedPayment.id(), new AllocationRequest(unassigned.id(), 10));
        FinancialReportView report = financialReportService.financialReport(List.of());
        assertThat(report.summary()).isEqualTo(new FinancialReportSummaryView(200, 100, 100, 200, -100));
        assertThat(report.subscriptions()).extracting(SubscriptionFinancialReportView::subscriptionId).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(report.subscriptions()).filteredOn(row -> row.subscriptionId().equals(first.id())).singleElement()
        .satisfies(row -> assertThat(row.payments()).singleElement().satisfies(allocation -> {
            assertThat(allocation.paymentId()).isEqualTo(payment.id());
            assertThat(allocation.personName()).isEqualTo("Два абонемента");
            assertThat(allocation.paidAt()).isEqualTo(date);
            assertThat(allocation.method()).isEqualTo(PaymentMethod.CASH);
            assertThat(allocation.chargeId()).isEqualTo(firstCharge.id());
            assertThat(allocation.allocated()).isEqualTo(60);
        }));
        assertThat(report.unassignedCharges()).singleElement().satisfies(row -> {
            assertThat(row.chargeId()).isEqualTo(unassigned.id());
            assertThat(row.amount()).isEqualTo(25);
            assertThat(row.allocated()).isEqualTo(10);
            assertThat(row.outstanding()).isEqualTo(15);
        });
    }

    @Test
    void financialReportCanSelectSeveralSubscriptions() {
        LocalDate date = LocalDate.now();
        pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, date, null));
        PersonView firstMember = personService.createPerson(new PersonRequest("Первый", null, null));
        PersonView secondMember = personService.createPerson(new PersonRequest("Второй", null, null));
        PersonView thirdMember = personService.createPerson(new PersonRequest("Третий", null, null));
        PersonView otherMember = personService.createPerson(new PersonRequest("Другой", null, null));
        SubscriptionView first = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(18, 0)), 3,"Трое", null));
        subscriptionService.addMember(first.id(), new MemberRequest(firstMember.id(), false, false, false));
        subscriptionService.addMember(first.id(), new MemberRequest(secondMember.id(), false, false, false));
        subscriptionService.addMember(first.id(), new MemberRequest(thirdMember.id(), false, false, false));
        SubscriptionView finalizedFirst = subscriptionFinalizationService.finalizeSubscription(first.id());
        SubscriptionView second = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 1,"Один", null));
        subscriptionService.addMember(second.id(), new MemberRequest(otherMember.id(), false, false, false));
        subscriptionFinalizationService.finalizeSubscription(second.id());
        ChargeView firstCharge = balanceService.balance(firstMember.id()).charges().getFirst();
        PaymentView firstPayment = paymentService.createPayment(new PaymentRequest(firstMember.id(), 34, date, PaymentMethod.CASH, null));
        paymentService.allocatePayment(firstPayment.id(), new AllocationRequest(firstCharge.id(), 34));
        ChargeView secondCharge = balanceService.balance(otherMember.id()).charges().getFirst();
        PaymentView secondPayment = paymentService.createPayment(new PaymentRequest(otherMember.id(), 100, date, PaymentMethod.CASH, null));
        paymentService.allocatePayment(secondPayment.id(), new AllocationRequest(secondCharge.id(), 100));
        FinancialReportView selected = financialReportService.financialReport(List.of(first.id()));
        assertThat(finalizedFirst.poolRentalAmount()).isEqualTo(100);
        assertThat(selected.summary()).isEqualTo(new FinancialReportSummaryView(102, 34, 68, 100, -66));
        assertThat(selected.subscriptions()).singleElement().satisfies(row -> assertThat(row).extracting(SubscriptionFinancialReportView::allocatedPayments, SubscriptionFinancialReportView::poolRentalCost, SubscriptionFinancialReportView::clubProfit).containsExactly(34L, 100L, -66L));
    }

    @Test
    void sortsKayakersAlphabeticallyInPeopleBalancesReportsAndSubscriptionMembers() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        PersonView zoe = personService.createPerson(new PersonRequest("Зоя", null, null));
        PersonView lowerCaseAliya = personService.createPerson(new PersonRequest("алия", null, null));
        PersonView alina = personService.createPerson(new PersonRequest("Алина", null, null));
        PersonView upperCaseAliya = personService.createPerson(new PersonRequest("Алия", null, null));
        PersonView boris = personService.createPerson(new PersonRequest("Борис", null, null));
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 5));
        subscriptionService.addMember(subscription.id(), new MemberRequest(zoe.id(), false, false, false));
        subscriptionService.addMember(subscription.id(), new MemberRequest(boris.id(), false, false, false));
        subscriptionService.addMember(subscription.id(), new MemberRequest(alina.id(), false, false, false));
        subscriptionService.addMember(subscription.id(), new MemberRequest(lowerCaseAliya.id(), false, false, false));
        subscriptionService.addMember(subscription.id(), new MemberRequest(upperCaseAliya.id(), false, false, false));
        assertThat(personService.listPeople()).extracting(PersonView::fullName).containsExactly("Алина","алия","Алия","Борис","Зоя");
        assertThat(balanceService.listPersonBalances()).extracting(PersonBalanceSummaryView::fullName).containsExactly("Алина","алия","Алия","Борис","Зоя");
        assertThat(subscriptionQueryService.getSubscription(subscription.id()).members()).extracting(MemberView::personName).containsExactly("Алина","алия","Алия","Борис","Зоя");
    }
}
