package kz.kayak.bookkeeper.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.BalanceView;
import kz.kayak.bookkeeper.api.dto.PersonBalanceSummaryView;
import kz.kayak.bookkeeper.api.dto.PersonSubscriptionBalanceView;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.Payment;
import kz.kayak.bookkeeper.domain.PaymentAllocation;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PaymentAllocationRepository;
import kz.kayak.bookkeeper.repository.PaymentRepository;
import kz.kayak.bookkeeper.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Reads personal ledger totals and club-wide kayaker balance summaries. */
@Service
public class BalanceService {
    private final PersonRepository people;
    private final ChargeRepository charges;
    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final LedgerViewMapper views;

    public BalanceService(
            PersonRepository people,
            ChargeRepository charges,
            PaymentRepository payments,
            PaymentAllocationRepository allocations,
            LedgerViewMapper views) {
        this.people = people;
        this.charges = charges;
        this.payments = payments;
        this.allocations = allocations;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<PersonBalanceSummaryView> listPersonBalances() {
        List<Charge> allCharges = charges.findAll();
        List<PaymentAllocation> allAllocations = allocations.findAll();
        Map<Long, List<Charge>> chargesByPerson = allCharges.stream()
                .collect(Collectors.groupingBy(charge -> charge.getPerson().getId()));
        Map<Long, Long> allocatedByCharge = allAllocations.stream()
                .collect(Collectors.groupingBy(allocation -> allocation.getCharge().getId(), Collectors.summingLong(PaymentAllocation::getAmount)));
        Map<Long, Long> paidByPerson = payments.findAll().stream().collect(Collectors.groupingBy(payment -> payment.getPerson().getId(), Collectors.summingLong(Payment::getAmount)));
        Map<Long, Long> allocatedByPerson = allAllocations.stream().collect(Collectors.groupingBy(allocation -> allocation.getPayment().getPerson().getId(), Collectors.summingLong(PaymentAllocation::getAmount)));
        return people.findAllOrderedByFullNameIgnoreCase().stream().map(person -> {
            List<Charge> personCharges = chargesByPerson.getOrDefault(person.getId(), List.of());
            long charged = personCharges.stream().mapToLong(Charge::getAmount).sum();
            long paid = paidByPerson.getOrDefault(person.getId(), 0L);
            long unallocated = paid - allocatedByPerson.getOrDefault(person.getId(), 0L);
            return new PersonBalanceSummaryView(person.getId(), person.getFullName(), person.isActive(), charged, paid, unallocated, charged - paid,
                    subscriptionBalances(personCharges, allocatedByCharge));
        }).toList();
    }

    private List<PersonSubscriptionBalanceView> subscriptionBalances(List<Charge> personCharges, Map<Long, Long> allocatedByCharge) {
        Map<Long, List<Charge>> groups = new LinkedHashMap<>();
        for (Charge charge : personCharges) {
            Long subscriptionId = charge.getSubscription() == null ? null : charge.getSubscription().getId();
            groups.computeIfAbsent(subscriptionId, ignored -> new ArrayList<>()).add(charge);
        }
        return groups.entrySet().stream().map(group -> {
            var subscription = group.getValue().getFirst().getSubscription();
            String title = subscription == null ? "Без абонемента" : subscription.getTitle();
            long charged = group.getValue().stream().mapToLong(Charge::getAmount).sum();
            long allocated = group.getValue().stream().mapToLong(charge -> allocatedByCharge.getOrDefault(charge.getId(), 0L)).sum();
            return new PersonSubscriptionBalanceView(group.getKey(), title, charged, allocated, charged - allocated);
        }).sorted(Comparator.comparing(PersonSubscriptionBalanceView::subscriptionId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public BalanceView balance(long id) {
        Person p = get(people, id, "Person");
        List<Charge> c = charges.findByPerson(p);
        List<Payment> paymentList = payments.findByPerson(p);
        long total = c.stream().mapToLong(Charge::getAmount).sum(), paid = paymentList.stream().mapToLong(Payment::getAmount).sum();
        return new BalanceView(id, total, paid, total - paid, c.stream().map(views::chargeView).toList(), paymentList.stream().map(views::paymentView).toList());
    }
}
