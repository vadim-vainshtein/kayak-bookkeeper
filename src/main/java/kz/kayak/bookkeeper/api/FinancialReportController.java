package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.FinancialReportView;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import kz.kayak.bookkeeper.service.FinancialReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Учёт тренировок", description = "Абонементы, тарифы и оплаты каяк-клуба")
public class FinancialReportController {
    private final FinancialReportService financialReportService;

    public FinancialReportController(FinancialReportService financialReportService) {
        this.financialReportService = financialReportService;
    }

    @Operation(summary = "Получить финансовый отчёт по абонементам", description = "Без subscriptionId возвращает все абонементы; повторяющийся subscriptionId ограничивает отчёт выбранными абонементами. Необязательный status (DRAFT, FINALIZED, COMPLETED) дополнительно ограничивает выборку. Непривязанные начисления выводятся отдельно и не фильтруются по статусу.")
    @GetMapping("/reports/financial")
    public FinancialReportView financialReport(@RequestParam(required = false) List<Long> subscriptionId,
                                              @RequestParam(required = false) SubscriptionStatus status) {
        return financialReportService.financialReport(subscriptionId, status);
    }
}
