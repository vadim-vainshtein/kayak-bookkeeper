package kz.kayak.bookkeeper.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.*;
import kz.kayak.bookkeeper.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FinancialReportCashTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Qyzylorda");
    @Autowired PersonService people;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired FinalizedSubscriptionEditService edits;
    @Autowired PricingRateService pricing;
    @Autowired BalanceService balances;
    @Autowired PaymentService payments;
    @Autowired ChargeService manualCharges;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionMemberRepository memberRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired PaymentAllocationRepository allocationRepository;
    @Autowired TrainingSessionRepository sessionRepository;
    @Autowired Clock reportClock;

    @Test
    void debitsExactSessionSnapshotsAtStartAndExcludesFutureReceiptsEvenAfterRatesAreDeleted() {
        LocalDate today = LocalDate.of(2030, 2, 10);
        var firstRate = pricing.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, today.minusDays(1), today.minusDays(1)));
        var secondRate = pricing.createRate(new RateRequest(RateCode.POOL_RENTAL, 200, today, today));
        var thirdRate = pricing.createRate(new RateRequest(RateCode.POOL_RENTAL, 300, today.plusDays(1), today.plusDays(1)));
        var person = people.createPerson(new PersonRequest("Касса", null, null));
        var subscription = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(
                today.minusDays(1).atTime(18, 0), today.atTime(18, 0), today.plusDays(1).atTime(18, 0)), 1));
        subscriptions.addMember(subscription.id(), new MemberRequest(person.id(), false, false, false));
        finalization.finalizeSubscription(subscription.id());
        var charge = balances.balance(person.id()).charges().getFirst();
        var received = payments.createPayment(new PaymentRequest(person.id(), 450, today, PaymentMethod.TRANSFER, null));
        payments.allocatePayment(received.id(), new AllocationRequest(charge.id(), 450));
        var future = payments.createPayment(new PaymentRequest(person.id(), 100, today.plusDays(1), PaymentMethod.CASH, null));
        payments.allocatePayment(future.id(), new AllocationRequest(charge.id(), 100));
        pricing.deleteRate(firstRate.id());
        pricing.deleteRate(secondRate.id());
        pricing.deleteRate(thirdRate.id());

        assertCash(subscription.id(), today.atTime(17, 59, 59), 350);
        assertCash(subscription.id(), today.atTime(18, 0), 150);
        assertCash(subscription.id(), today.atTime(18, 0, 1), 150);
        assertCash(subscription.id(), today.plusDays(1).atTime(18, 0), -50);
        assertThat(report(today.atTime(18, 0)).financialReport(List.of(subscription.id())).summary())
                .satisfies(summary -> {
                    assertThat(summary.allocatedPayments()).isEqualTo(550);
                    assertThat(summary.poolRentalCost()).isEqualTo(600);
                    assertThat(summary.clubProfit()).isEqualTo(-50);
                });
    }

    @Test
    void recalculatedScheduleSnapshotsNewDateRatesAndSummaryRespectsSelectedStatus() {
        LocalDate date = LocalDate.of(2031, 3, 10);
        pricing.createRate(new RateRequest(RateCode.POOL_RENTAL, 100, date, date));
        pricing.createRate(new RateRequest(RateCode.POOL_RENTAL, 250, date.plusDays(1), null));
        var person = people.createPerson(new PersonRequest("Пересчёт", null, null));
        var member = new MemberRequest(person.id(), false, false, false);
        var subscription = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(18, 0)), 1));
        subscriptions.addMember(subscription.id(), member);
        finalization.finalizeSubscription(subscription.id());
        edits.updateFinalizedSubscription(subscription.id(), new FinalizedSubscriptionUpdateRequest(
                List.of(date.plusDays(1).atTime(18, 0)), List.of(member), 1, null, null, false));
        assertCash(subscription.id(), date.plusDays(1).atTime(17, 59), 0);
        assertCash(subscription.id(), date.plusDays(1).atTime(18, 0), -250);

        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(18, 0)), 1));
        var charge = manualCharges.createManualCharge(new ManualChargeRequest(person.id(), 30, date, "Черновик", draft.id()));
        var paid = payments.createPayment(new PaymentRequest(person.id(), 30, date, PaymentMethod.CASH, null));
        payments.allocatePayment(paid.id(), new AllocationRequest(charge.id(), 30));
        var reports = report(date.plusDays(1).atTime(18, 0));
        assertThat(reports.financialReport(List.of()).summary().cashBalance()).isEqualTo(-220);
        assertThat(reports.financialReport(List.of(), SubscriptionStatus.DRAFT).summary().cashBalance()).isEqualTo(30);
        assertThat(reports.financialReport(List.of(subscription.id()), SubscriptionStatus.DRAFT).summary().cashBalance()).isZero();
        subscriptions.completeSubscription(subscription.id());
        assertThat(reports.financialReport(List.of(), SubscriptionStatus.COMPLETED).summary().cashBalance()).isEqualTo(-250);
    }

    @Test
    void reportClockUsesClubTimezoneRatherThanHostTimezone() {
        assertThat(reportClock.getZone()).isEqualTo(ZONE);
    }

    private void assertCash(long subscriptionId, LocalDateTime now, long expected) {
        var report = report(now).financialReport(List.of(subscriptionId));
        assertThat(report.summary().cashBalance()).isEqualTo(expected);
        assertThat(report.subscriptions()).singleElement().satisfies(row -> assertThat(row.cashBalance()).isEqualTo(expected));
    }

    private FinancialReportService report(LocalDateTime now) {
        return new FinancialReportService(subscriptionRepository, memberRepository, chargeRepository,
                allocationRepository, sessionRepository, Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
    }
}
