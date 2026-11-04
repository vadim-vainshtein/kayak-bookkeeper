package kz.kayak.bookkeeper.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.BalanceView;
import kz.kayak.bookkeeper.api.dto.PersonBalanceSummaryView;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.Payment;
import kz.kayak.bookkeeper.domain.PaymentAllocation;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PaymentAllocationRepository;
import kz.kayak.bookkeeper.repository.PaymentRepository;
import kz.kayak.bookkeeper.repository.PersonRepository;
import org.springframework.stereotype.Service;

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

    public List<PersonBalanceSummaryView> listPersonBalances() {
        Map<Long, Long> chargedByPerson = charges.findAll().stream().collect(Collectors.groupingBy(charge -> charge.getPerson().getId(), Collectors.summingLong(Charge::getAmount)));
        Map<Long, Long> paidByPerson = payments.findAll().stream().collect(Collectors.groupingBy(payment -> payment.getPerson().getId(), Collectors.summingLong(Payment::getAmount)));
        Map<Long, Long> allocatedByPerson = allocations.findAll().stream().collect(Collectors.groupingBy(allocation -> allocation.getPayment().getPerson().getId(), Collectors.summingLong(PaymentAllocation::getAmount)));
        return people.findAllOrderedByFullNameIgnoreCase().stream().map(person -> {
            long charged = chargedByPerson.getOrDefault(person.getId(), 0L);
            long paid = paidByPerson.getOrDefault(person.getId(), 0L);
            long unallocated = paid - allocatedByPerson.getOrDefault(person.getId(), 0L);
            return new PersonBalanceSummaryView(person.getId(), person.getFullName(), person.isActive(), charged, paid, unallocated, charged - paid);
        }).toList();
    }

    public BalanceView balance(long id) {
        Person p = get(people, id, "Person");
        List<Charge> c = charges.findByPerson(p);
        List<Payment> paymentList = payments.findByPerson(p);
        long total = c.stream().mapToLong(Charge::getAmount).sum(), paid = paymentList.stream().mapToLong(Payment::getAmount).sum();
        return new BalanceView(id, total, paid, total - paid, c.stream().map(views::chargeView).toList(), paymentList.stream().map(views::paymentView).toList());
    }
}
