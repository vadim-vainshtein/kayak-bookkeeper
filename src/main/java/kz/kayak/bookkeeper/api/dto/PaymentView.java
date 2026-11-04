package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDate;
import java.util.List;
import kz.kayak.bookkeeper.domain.PaymentMethod;

public record PaymentView(
        Long id,
        Long personId,
        long amount,
        LocalDate paidAt,
        PaymentMethod method,
        String note,
        List<PaymentAllocationView> allocations
) {
}
