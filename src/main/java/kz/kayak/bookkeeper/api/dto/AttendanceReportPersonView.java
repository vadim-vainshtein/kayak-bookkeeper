package kz.kayak.bookkeeper.api.dto;

import java.util.List;

public record AttendanceReportPersonView(
        Long personId, String personName, boolean currentMember,
        long memberVisits, long guestVisits, long totalVisits,
        List<AttendanceReportVisitView> visits
) {}
