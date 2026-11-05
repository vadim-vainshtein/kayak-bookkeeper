package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDate;
import kz.kayak.bookkeeper.domain.ChargeType;

public record ChargeView(
        Long id,
        ChargeType type,
        long amount,
        long allocated,
        LocalDate chargedAt,
        String description,
        Long subscriptionId
) {
}
