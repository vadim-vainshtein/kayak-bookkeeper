package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Еженедельный абонемент с участниками; сохраняется одной транзакцией")
public record RecurringSubscriptionWithMembersRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalDate startDate,
        @Positive int sessionCount,
        @Min(1) @Max(8) Integer capacity,
        @Size(max = 255) String title,
        @Size(max = 1000) String description,
        @NotNull @Size(min = 1, max = 8) List<@jakarta.validation.Valid MemberRequest> members
) {
}
