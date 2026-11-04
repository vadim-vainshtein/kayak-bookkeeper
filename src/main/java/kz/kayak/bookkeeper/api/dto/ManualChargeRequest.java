package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

@Schema(description = "Ручное начисление для частного случая")
public record ManualChargeRequest(
        @NotNull Long personId,
        @Positive long amount,
        @NotNull LocalDate chargedAt,
        @NotBlank String description,
        Long subscriptionId
) {
}
