package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDateTime;

public record AttendanceReportSessionView(
        Long sessionId, LocalDateTime startsAt, boolean started, boolean recorded,
        long memberVisits, long guestVisits, long totalVisits
) {}
