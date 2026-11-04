package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Обновление карточки каякера")
public record PersonUpdateRequest(
        @Schema(description = "Полное имя", example = "Алия Абдрахманова") @NotBlank String fullName,
        @Schema(description = "Номер телефона", example = "+77011234567") String phone,
        @Schema(description = "Внутренняя заметка") String note,
        @Schema(description = "Активен ли каякер; неактивного нельзя добавить в новый абонемент", example = "true") @NotNull Boolean active
) {
}
