package kz.kayak.bookkeeper.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Either personId, or a new person's name and optional phone. Null amount uses today's rate. */
public record AttendanceGuestRequest(
        @Positive Long personId,
        @Size(max = 255) String fullName,
        @Size(max = 255) String phone,
        @PositiveOrZero Long amount
) {}
