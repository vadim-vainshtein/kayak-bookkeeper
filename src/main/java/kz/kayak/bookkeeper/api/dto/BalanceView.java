package kz.kayak.bookkeeper.api.dto;

import java.util.List;

public record BalanceView(
        Long personId,
        long charged,
        long paid,
        long balance,
        List<ChargeView> charges,
        List<PaymentView> payments
) {
}
