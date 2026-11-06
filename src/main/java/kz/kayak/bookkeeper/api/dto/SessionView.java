package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDateTime;

public record SessionView(
        Long id,
        LocalDateTime startsAt,
        long attendedCount,
        boolean attendanceRecorded,
        boolean attendanceAvailable
) {
}
