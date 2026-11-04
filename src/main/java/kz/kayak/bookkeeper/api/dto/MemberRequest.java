package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Участник абонемента и используемое клубное снаряжение")
public record MemberRequest(
        @NotNull Long personId,
        boolean usesSkirt,
        boolean usesPaddle,
        boolean usesKayak
) {
}
