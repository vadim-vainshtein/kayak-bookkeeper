package kz.kayak.bookkeeper.api.dto;

public record AttendancePersonView(
        Long personId, String personName, boolean guest, boolean present,
        Long amount, Long chargeId, long allocated
) {}
