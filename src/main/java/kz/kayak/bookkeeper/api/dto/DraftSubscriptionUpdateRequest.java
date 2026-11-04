package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Полная замена расписания и состава участников черновика абонемента")
public record DraftSubscriptionUpdateRequest(
        @NotEmpty List<@NotNull LocalDateTime> startsAt,
        @NotEmpty @Size(max = 8) List<@jakarta.validation.Valid MemberRequest> members,
        @Min(1) @Max(8) Integer capacity,
        @Size(max = 255) String title,
        @Size(max = 1000) String description
) {
}
