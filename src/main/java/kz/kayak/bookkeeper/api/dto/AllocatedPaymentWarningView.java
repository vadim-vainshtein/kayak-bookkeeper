package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Распределённая оплата, которую затрагивает редактирование рассчитанного абонемента")
public record AllocatedPaymentWarningView(
        Long paymentId,
        LocalDate paidAt,
        long paymentAmount,
        long allocatedAmount,
        Long chargeId,
        String chargeDescription
) {
}
