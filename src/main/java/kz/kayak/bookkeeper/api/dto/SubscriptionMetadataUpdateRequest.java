package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Редактирование названия и описания абонемента. Не изменяет расписание, участников, вместимость или зафиксированные начисления.")
public record SubscriptionMetadataUpdateRequest(
        @Schema(description = "Необязательное название", example = "Ноябрь, вторник") @Size(max = 255) String title,
        @Schema(description = "Необязательное описание") @Size(max = 1000) String description
) {
}
