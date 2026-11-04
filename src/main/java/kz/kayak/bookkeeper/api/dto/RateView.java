package kz.kayak.bookkeeper.api.dto;

import java.time.LocalDate;
import kz.kayak.bookkeeper.domain.RateCode;

public record RateView(
        Long id,
        RateCode code,
        long amount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
