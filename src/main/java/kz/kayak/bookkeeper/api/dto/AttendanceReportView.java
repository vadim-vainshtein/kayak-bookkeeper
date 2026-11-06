package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AttendanceReportView(
        LocalDateTime generatedAt, String timeZone, AttendanceReportSummaryView summary,
        List<SubscriptionAttendanceReportView> subscriptions
) {}
