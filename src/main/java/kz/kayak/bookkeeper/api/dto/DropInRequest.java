package kz.kayak.bookkeeper.api.dto;

import jakarta.validation.constraints.NotNull;

public record DropInRequest(
        @NotNull Long personId
) {
}
