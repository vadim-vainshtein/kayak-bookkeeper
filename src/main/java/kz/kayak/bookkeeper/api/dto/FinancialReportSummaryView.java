package kz.kayak.bookkeeper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Сводные показатели финансового отчёта по выбранным абонементам за всё время")
public record FinancialReportSummaryView(
        long charged,
        long allocatedPayments,
        long outstanding,
        long poolRentalCost,
        long clubProfit,
        @Schema(description = "Оплаты, полученные к текущей дате, минус аренда начавшихся тренировок")
        long cashBalance
) {
}
