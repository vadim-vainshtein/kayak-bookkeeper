package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AttendanceView(
        Long sessionId, LocalDateTime startsAt, long revision, boolean available,
        LocalDate rateDate, Long dropInAmount, List<AttendancePersonView> people
) {}
