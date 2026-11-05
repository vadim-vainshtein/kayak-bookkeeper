package kz.kayak.bookkeeper.service;

import java.util.List;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.PaymentAllocationView;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.Payment;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PaymentAllocationRepository;
import org.springframework.stereotype.Service;

/** Maps persisted charges and payments with their allocation details. */
@Service
public class LedgerViewMapper {
    private final PaymentAllocationRepository allocations;
    private final ChargeRepository charges;

    public LedgerViewMapper(PaymentAllocationRepository allocations, ChargeRepository charges) {
        this.allocations = allocations;
        this.charges = charges;
    }

    public PaymentView paymentView(Payment payment) {
        List<PaymentAllocationView> paymentAllocations = allocations.findByPayment(payment).stream()
                .map(allocation -> new PaymentAllocationView(allocation.getId(), allocation.getCharge().getId(), allocation.getCharge().getDescription(), allocation.getAmount()))
                .toList();
        return new PaymentView(payment.getId(), payment.getPerson().getId(), payment.getAmount(), payment.getPaidAt(), payment.getMethod(), payment.getNote(), paymentAllocations);
    }

    public ChargeView chargeView(Charge c) {
        return new ChargeView(c.getId(), c.getType(), c.getAmount(), charges.allocatedFor(c), c.getChargedAt(), c.getDescription(), c.getSubscription() == null ? null : c.getSubscription().getId());
    }
}
