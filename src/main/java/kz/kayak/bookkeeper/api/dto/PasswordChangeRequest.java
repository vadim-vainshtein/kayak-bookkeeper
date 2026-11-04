package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Смена пароля текущего пользователя")
public record PasswordChangeRequest(
        @NotBlank @Schema(description = "Текущий пароль") String currentPassword,
        @NotBlank @Size(min = 8, max = 72) @Schema(description = "Новый пароль, от 8 до 72 символов") String newPassword
) {
}
