package kz.kayak.bookkeeper.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record AttendanceUpdateRequest(
        @NotNull @PositiveOrZero Long revision,
        @NotNull List<@NotNull @Positive Long> memberIds,
        @NotNull List<@NotNull @Valid AttendanceGuestRequest> guests
) {}
