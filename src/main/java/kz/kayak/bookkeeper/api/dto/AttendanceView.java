package kz.kayak.bookkeeper.api.dto;

import kz.kayak.bookkeeper.domain.AttendanceStatus;
import kz.kayak.bookkeeper.domain.AttendanceType;

public record AttendanceView(
        Long id,
        Long personId,
        String personName,
        AttendanceType type,
        AttendanceStatus status
) {
}
