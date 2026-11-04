package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import kz.kayak.bookkeeper.domain.PaymentMethod;

@Schema(description = "Платёж и необязательное распределение на одно начисление; сохраняются одной транзакцией")
public record PaymentWithAllocationRequest(
        @NotNull Long personId,
        @Positive long amount,
        @NotNull LocalDate paidAt,
        @NotNull PaymentMethod method,
        String note,
        Long chargeId,
        @Positive Long allocationAmount
) {
}
