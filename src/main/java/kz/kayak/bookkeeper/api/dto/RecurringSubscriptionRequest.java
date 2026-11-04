package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Абонемент с еженедельным расписанием")
public record RecurringSubscriptionRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @Schema(description = "Дата начала расчёта; при совпадении с dayOfWeek включается в список", example = "2026-11-10") @NotNull LocalDate startDate,
        @Positive int sessionCount,
        @Schema(description = "Вместимость, от 1 до 8; по умолчанию 8", example = "8") @Min(1) @Max(8) Integer capacity,
        @Schema(description = "Необязательное название абонемента", example = "Ноябрь, вторник") @Size(max = 255) String title,
        @Schema(description = "Необязательное описание абонемента") @Size(max = 1000) String description
) {
}
