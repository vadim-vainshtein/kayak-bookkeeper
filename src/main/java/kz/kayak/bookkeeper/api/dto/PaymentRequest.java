package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import kz.kayak.bookkeeper.domain.PaymentMethod;

@Schema(description = "Полученный платёж")
public record PaymentRequest(
        @NotNull Long personId,
        @Schema(description = "Сумма в тенге", example = "15000") @Positive long amount,
        @NotNull LocalDate paidAt,
        @NotNull PaymentMethod method,
        String note
) {
}
