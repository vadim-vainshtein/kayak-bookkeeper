package kz.kayak.bookkeeper.api.dto;

import java.util.List;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;

public record SubscriptionAttendanceReportView(
        Long subscriptionId, String title, SubscriptionStatus status, int memberCount,
        AttendanceReportSummaryView summary,
        List<AttendanceReportSessionView> sessions, List<AttendanceReportPersonView> people
) {}
