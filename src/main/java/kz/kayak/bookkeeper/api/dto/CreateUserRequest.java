package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import kz.kayak.bookkeeper.domain.UserRole;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\s:]+")
        @Schema(description = "Уникальный логин без пробелов и двоеточий") String login,
        @NotBlank @Size(min = 8, max = 72)
        @Schema(description = "Пароль от 8 символов, не более 72 байт UTF-8", accessMode = Schema.AccessMode.WRITE_ONLY) String password,
        @Schema(description = "Роль; по умолчанию READ_ONLY") UserRole role
) {
    public CreateUserRequest {
        if (role == null) role = UserRole.READ_ONLY;
    }

    @AssertTrue(message = "Пароль не должен превышать 72 байта UTF-8")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
