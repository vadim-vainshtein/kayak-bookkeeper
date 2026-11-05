package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Начисления и зачтённые оплаты каякера по абонементу; null subscriptionId — без абонемента")
public record PersonSubscriptionBalanceView(
        Long subscriptionId,
        String title,
        long charged,
        long allocated,
        long balance
) {
}
