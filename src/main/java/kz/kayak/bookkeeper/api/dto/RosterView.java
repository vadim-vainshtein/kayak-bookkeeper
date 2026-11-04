package kz.kayak.bookkeeper.api.dto;

import java.util.List;

public record RosterView(
        Long sessionId,
        int capacity,
        long vacanciesForDropIns,
        List<AttendanceView> attendance
) {
}
