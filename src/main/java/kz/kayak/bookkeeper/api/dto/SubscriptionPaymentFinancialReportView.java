package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import kz.kayak.bookkeeper.domain.PaymentMethod;

@Schema(description = "Распределение оплаты на начисление абонемента")
public record SubscriptionPaymentFinancialReportView(
        Long paymentId,
        Long personId,
        String personName,
        LocalDate paidAt,
        PaymentMethod method,
        String paymentNote,
        Long chargeId,
        String chargeDescription,
        long allocated
) {
}
