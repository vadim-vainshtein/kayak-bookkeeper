package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kz.kayak.bookkeeper.domain.AttendanceStatus;

@Schema(description = "Отметка переклички участника абонемента")
public record AttendanceRequest(
        @NotNull Long personId,
        @Schema(example = "PRESENT") @NotNull AttendanceStatus status
) {
}
