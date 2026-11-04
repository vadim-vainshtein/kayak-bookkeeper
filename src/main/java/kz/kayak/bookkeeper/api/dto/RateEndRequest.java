package kz.kayak.bookkeeper.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RateEndRequest(
        @NotNull LocalDate effectiveTo
) {
}
