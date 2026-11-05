package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.FinancialReportSummaryView;
import kz.kayak.bookkeeper.api.dto.FinancialReportView;
import kz.kayak.bookkeeper.api.dto.SubscriptionFinancialReportView;
import kz.kayak.bookkeeper.api.dto.SubscriptionPaymentFinancialReportView;
import kz.kayak.bookkeeper.api.dto.UnassignedChargeFinancialReportView;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.Payment;
import kz.kayak.bookkeeper.domain.PaymentAllocation;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionMember;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PaymentAllocationRepository;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Builds financial reports from subscription charges and payment allocations. */
@Service
public class FinancialReportService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionMemberRepository members;
    private final ChargeRepository charges;
    private final PaymentAllocationRepository allocations;

    public FinancialReportService(
            SubscriptionRepository subscriptions,
            SubscriptionMemberRepository members,
            ChargeRepository charges,
            PaymentAllocationRepository allocations) {
        this.subscriptions = subscriptions;
        this.members = members;
        this.charges = charges;
        this.allocations = allocations;
    }

    @Transactional
    public FinancialReportView financialReport(List<Long> subscriptionIds) {
        return financialReport(subscriptionIds, null);
    }

    @Transactional
    public FinancialReportView financialReport(List<Long> subscriptionIds, SubscriptionStatus status) {
        List<Subscription> reportSubscriptions = selectedSubscriptions(subscriptionIds).stream()
                .filter(subscription -> status == null || subscription.getStatus() == status)
                .toList();
        Set<Long> selectedIds = reportSubscriptions.stream().map(Subscription::getId).collect(Collectors.toSet());
        List<Charge> allCharges = charges.findAll();
        List<PaymentAllocation> allAllocations = allocations.findAll();
        List<Charge> reportCharges = allCharges.stream()
                .filter(charge -> charge.getSubscription() != null && selectedIds.contains(charge.getSubscription().getId()))
                .toList();
        Map<Long, Long> allocatedByCharge = allAllocations.stream()
                .collect(Collectors.groupingBy(allocation -> allocation.getCharge().getId(), Collectors.summingLong(PaymentAllocation::getAmount)));
        Map<Long, List<Charge>> chargesBySubscription = reportCharges.stream()
                .collect(Collectors.groupingBy(charge -> charge.getSubscription().getId()));
        Map<Long, List<SubscriptionMember>> membersBySubscription = reportSubscriptions.isEmpty() ? Map.of()
                : members.findBySubscriptionInWithSubscriptionAndPerson(reportSubscriptions).stream()
                .collect(Collectors.groupingBy(member -> member.getSubscription().getId()));
        List<SubscriptionFinancialReportView> subscriptionRows = reportSubscriptions.stream()
                .map(subscription -> subscriptionReport(subscription,
                        chargesBySubscription.getOrDefault(subscription.getId(), List.of()),
                        allocatedByCharge, membersBySubscription.getOrDefault(subscription.getId(), List.of()),
                        allAllocations))
                .sorted(Comparator.comparing(SubscriptionFinancialReportView::title,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(SubscriptionFinancialReportView::subscriptionId))
                .toList();
        long charged = subscriptionRows.stream().mapToLong(SubscriptionFinancialReportView::charged).sum();
        long allocated = subscriptionRows.stream().mapToLong(SubscriptionFinancialReportView::allocatedPayments).sum();
        long outstanding = subscriptionRows.stream().mapToLong(SubscriptionFinancialReportView::outstanding).sum();
        long poolRentalCost = subscriptionRows.stream().mapToLong(SubscriptionFinancialReportView::poolRentalCost).sum();
        List<UnassignedChargeFinancialReportView> unassignedCharges = allCharges.stream()
                .filter(charge -> charge.getSubscription() == null)
                .map(charge -> unassignedChargeReport(charge, allocatedByCharge.getOrDefault(charge.getId(), 0L)))
                .sorted(Comparator.comparing(UnassignedChargeFinancialReportView::personName,
                        String.CASE_INSENSITIVE_ORDER).thenComparing(UnassignedChargeFinancialReportView::chargeId))
                .toList();
        return new FinancialReportView(new FinancialReportSummaryView(charged, allocated, outstanding,
                poolRentalCost, allocated - poolRentalCost), subscriptionRows, unassignedCharges);
    }

    private List<Subscription> selectedSubscriptions(List<Long> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) return subscriptions.findAll();
        return new ArrayList<>(new LinkedHashSet<>(subscriptionIds)).stream()
                .map(id -> get(subscriptions, id, "Subscription"))
                .toList();
    }

    private SubscriptionFinancialReportView subscriptionReport(Subscription subscription,
                                                                   List<Charge> subscriptionCharges,
                                                                   Map<Long, Long> allocatedByCharge,
                                                                   List<SubscriptionMember> subscriptionMembers,
                                                                   List<PaymentAllocation> allAllocations) {
        long charged = subscriptionCharges.stream().mapToLong(Charge::getAmount).sum();
        long allocated = subscriptionCharges.stream()
                .mapToLong(charge -> allocatedByCharge.getOrDefault(charge.getId(), 0L)).sum();
        long poolRentalCost = Objects.requireNonNullElse(subscription.getPoolRentalAmount(), 0L);
        List<SubscriptionPaymentFinancialReportView> paymentDetails = allAllocations.stream()
                .filter(allocation -> allocation.getCharge().getSubscription() != null
                        && subscription.getId().equals(allocation.getCharge().getSubscription().getId()))
                .map(this::subscriptionPaymentReport)
                .sorted(Comparator.comparing(SubscriptionPaymentFinancialReportView::paidAt)
                        .thenComparing(SubscriptionPaymentFinancialReportView::paymentId)
                        .thenComparing(SubscriptionPaymentFinancialReportView::chargeId))
                .toList();
        return new SubscriptionFinancialReportView(subscription.getId(), subscription.getTitle(),
                subscription.getStatus(), subscriptionMembers.size(), charged, allocated, charged - allocated,
                poolRentalCost, allocated - poolRentalCost, paymentDetails);
    }

    private SubscriptionPaymentFinancialReportView subscriptionPaymentReport(PaymentAllocation allocation) {
        Payment payment = allocation.getPayment();
        Charge charge = allocation.getCharge();
        return new SubscriptionPaymentFinancialReportView(payment.getId(), payment.getPerson().getId(),
                payment.getPerson().getFullName(), payment.getPaidAt(), payment.getMethod(), payment.getNote(),
                charge.getId(), charge.getDescription(), allocation.getAmount());
    }

    private UnassignedChargeFinancialReportView unassignedChargeReport(Charge charge, long allocated) {
        return new UnassignedChargeFinancialReportView(charge.getId(), charge.getPerson().getId(),
                charge.getPerson().getFullName(), charge.getType(), charge.getDescription(), charge.getAmount(),
                allocated, charge.getAmount() - allocated);
    }
}
