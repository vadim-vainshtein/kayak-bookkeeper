package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Полное редактирование рассчитанного, но ещё не завершённого абонемента")
public record FinalizedSubscriptionUpdateRequest(
        @NotEmpty List<@NotNull LocalDateTime> startsAt,
        @NotEmpty @Size(max = 8) List<@jakarta.validation.Valid MemberRequest> members,
        @NotNull @Min(1) @Max(8) Integer capacity,
        @Size(max = 255) String title,
        @Size(max = 1000) String description,
        @Schema(description = "Подтверждает изменение начислений с уже распределёнными оплатами") boolean confirmAllocatedPayments
) {
}
