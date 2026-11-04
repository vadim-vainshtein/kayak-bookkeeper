package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import kz.kayak.bookkeeper.domain.RateCode;

@Schema(description = "Тариф за одну тренировку с периодом действия")
public record RateRequest(
        @Schema(description = "Вид тарифа", example = "POOL_RENTAL") @NotNull RateCode code,
        @Schema(description = "Сумма в тенге", example = "30000") @PositiveOrZero long amount,
        @Schema(description = "Дата начала действия", example = "2026-11-01") @NotNull LocalDate effectiveFrom,
        @Schema(description = "Дата окончания; null — бессрочно", nullable = true) LocalDate effectiveTo
) {
}
