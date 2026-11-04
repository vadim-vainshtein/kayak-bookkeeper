package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Данные нового каякера")
public record PersonRequest(
        @Schema(description = "Полное имя", example = "Алия Абдрахманова") @NotBlank String fullName,
        @Schema(description = "Номер телефона", example = "+77011234567") String phone,
        @Schema(description = "Внутренняя заметка") String note
) {
}
