package kz.kayak.bookkeeper.api.dto;

public record AttendanceReportSummaryView(
        long scheduledSessions, long startedSessions, long recordedSessions,
        long memberVisits, long guestVisits, long totalVisits, long uniqueVisitors
) {}
