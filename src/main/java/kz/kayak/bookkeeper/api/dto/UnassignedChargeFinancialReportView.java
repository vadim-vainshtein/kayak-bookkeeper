package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kz.kayak.bookkeeper.domain.ChargeType;

@Schema(description = "Начисление, не привязанное к абонементу")
public record UnassignedChargeFinancialReportView(
        Long chargeId,
        Long personId,
        String personName,
        ChargeType type,
        String description,
        long amount,
        long allocated,
        long outstanding
) {
}
