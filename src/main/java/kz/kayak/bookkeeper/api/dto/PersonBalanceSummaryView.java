package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Краткий баланс каякера для списка")
public record PersonBalanceSummaryView(
        Long personId,
        String fullName,
        boolean active,
        long charged,
        long paid,
        long unallocated,
        long balance,
        List<PersonSubscriptionBalanceView> subscriptionBalances
) {
}
