package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.util.NoSuchElementException;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.PaymentAmountRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PaymentWithAllocationRequest;
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

/** Registers payments and allocates them under payment and charge locks. */
@Service
public class PaymentService {
    private final PersonRepository people;
    private final PaymentRepository payments;
    private final ChargeRepository charges;
    private final PaymentAllocationRepository allocations;
    private final LedgerViewMapper views;

    public PaymentService(
            PersonRepository people,
            PaymentRepository payments,
            ChargeRepository charges,
            PaymentAllocationRepository allocations,
            LedgerViewMapper views) {
        this.people = people;
        this.payments = payments;
        this.charges = charges;
        this.allocations = allocations;
        this.views = views;
    }

    public PaymentView createPayment(PaymentRequest r) {
        Person p = get(people, r.personId(), "Person");
        return views.paymentView(payments.save(new Payment(p, r.amount(), r.paidAt(), r.method(), r.note())));
    }

    @Transactional
    public PaymentView createPaymentWithAllocation(PaymentWithAllocationRequest r) {
        if ((r.chargeId() == null) != (r.allocationAmount() == null))
            throw new DomainException("Choose both a charge and an allocation amount, or neither");
        Person person = get(people, r.personId(), "Person");
        Charge charge = r.chargeId() == null ? null : lockedCharge(r.chargeId());
        if (charge != null) validateAllocation(person, r.amount(), charge, r.allocationAmount());
        Payment payment = payments.save(new Payment(person, r.amount(), r.paidAt(), r.method(), r.note()));
        if (charge != null) allocations.save(new PaymentAllocation(payment, charge, r.allocationAmount()));
        return views.paymentView(payment);
    }

    @Transactional
    public void allocatePayment(long id, AllocationRequest r) {
        Payment p = lockedPayment(id);
        Charge c = lockedCharge(r.chargeId());
        if (!p.getPerson().getId().equals(c.getPerson().getId()))
            throw new DomainException("Payment and charge must belong to the same person");
        if (payments.allocatedFor(p) + r.amount() > p.getAmount())
            throw new DomainException("Allocation exceeds payment amount");
        if (charges.allocatedFor(c) + r.amount() > c.getAmount())
            throw new DomainException("Allocation exceeds charge amount");
        allocations.save(new PaymentAllocation(p, c, r.amount()));
    }

    @Transactional
    public PaymentView editPaymentAmount(long id, PaymentAmountRequest request) {
        Payment payment = lockedPayment(id);
        long allocated = payments.allocatedFor(payment);
        if (request.amount() <= 0) throw new DomainException("Сумма оплаты должна быть положительной.");
        if (request.amount() < allocated)
            throw new DomainException("Сумма оплаты не может быть меньше распределённой суммы: " + allocated + " ₸.");
        payment.setAmount(request.amount());
        if (request.paidAt() != null) payment.setPaidAt(request.paidAt());
        if (request.note() != null) payment.setNote(request.note().isEmpty() ? null : request.note());
        return views.paymentView(payment);
    }

    @Transactional
    public void cancelAllocation(long paymentId, long allocationId) {
        Payment payment = lockedPayment(paymentId);
        PaymentAllocation allocation = allocations.findById(allocationId)
                .filter(candidate -> candidate.getPayment().getId().equals(payment.getId()))
                .orElseThrow(() -> new NoSuchElementException("Распределение оплаты не найдено"));
        lockedCharge(allocation.getCharge().getId());
        allocations.delete(allocation);
    }

    private void validateAllocation(Person person, long paymentAmount, Charge charge, long allocationAmount) {
        if (!person.getId().equals(charge.getPerson().getId()))
            throw new DomainException("Payment and charge must belong to the same person");
        if (allocationAmount > paymentAmount) throw new DomainException("Allocation exceeds payment amount");
        if (charges.allocatedFor(charge) + allocationAmount > charge.getAmount())
            throw new DomainException("Allocation exceeds charge amount");
    }

    private Payment lockedPayment(long id) {
        return payments.findWithLockById(id).orElseThrow(() -> new NoSuchElementException("Payment " + id + " was not found"));
    }

    private Charge lockedCharge(long id) {
        return charges.findWithLockById(id).orElseThrow(() -> new NoSuchElementException("Charge " + id + " was not found"));
    }
}
