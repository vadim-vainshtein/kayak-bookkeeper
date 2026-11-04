package kz.kayak.bookkeeper.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record ChargeEditRequest(
        @Positive long amount,
        @NotNull LocalDate chargedAt,
        String description,
        Long subscriptionId,
        boolean updateSubscription
) {
    public ChargeEditRequest(long amount, LocalDate chargedAt, String description) {
        this(amount, chargedAt, description, null, false);
    }
    public ChargeEditRequest(long amount, LocalDate chargedAt, String description, Long subscriptionId) {
        this(amount, chargedAt, description, subscriptionId, true);
    }
}
