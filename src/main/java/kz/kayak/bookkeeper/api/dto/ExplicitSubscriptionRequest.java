package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Абонемент с перечисленными датами и временем тренировок")
public record ExplicitSubscriptionRequest(
        @Schema(description = "Даты и время тренировок", example = "[\"2026-11-10T19:00:00\", \"2026-11-17T19:00:00\"]") @NotEmpty List<@NotNull LocalDateTime> startsAt,
        @Schema(description = "Вместимость, от 1 до 8; по умолчанию 8", example = "8") @Min(1) @Max(8) Integer capacity,
        @Schema(description = "Необязательное название абонемента", example = "Ноябрь, вторник") @Size(max = 255) String title,
        @Schema(description = "Необязательное описание абонемента") @Size(max = 1000) String description
) {
    public ExplicitSubscriptionRequest(List<LocalDateTime> startsAt, Integer capacity) {
        this(startsAt, capacity, null, null);
    }
}
