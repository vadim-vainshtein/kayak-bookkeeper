package kz.kayak.bookkeeper.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AllocationRequest(
        @NotNull Long chargeId,
        @Positive long amount
) {
}
