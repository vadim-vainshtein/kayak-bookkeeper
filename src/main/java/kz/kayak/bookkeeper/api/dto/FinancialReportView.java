package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Финансовый отчёт по выбранным абонементам за всё время; непривязанные начисления выводятся отдельно")
public record FinancialReportView(
        FinancialReportSummaryView summary,
        List<SubscriptionFinancialReportView> subscriptions,
        List<UnassignedChargeFinancialReportView> unassignedCharges
) {
}
