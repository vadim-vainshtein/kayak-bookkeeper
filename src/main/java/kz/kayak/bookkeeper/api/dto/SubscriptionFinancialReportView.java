package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;

@Schema(description = "Финансовые показатели одного абонемента")
public record SubscriptionFinancialReportView(
        Long subscriptionId,
        String title,
        SubscriptionStatus status,
        int memberCount,
        long charged,
        long allocatedPayments,
        long outstanding,
        long poolRentalCost,
        long clubProfit,
        List<SubscriptionPaymentFinancialReportView> payments
) {
}
