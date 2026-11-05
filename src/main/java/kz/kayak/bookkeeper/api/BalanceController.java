package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.BalanceView;
import kz.kayak.bookkeeper.api.dto.PersonBalanceSummaryView;
import kz.kayak.bookkeeper.service.BalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Учёт тренировок", description = "Абонементы, тарифы и оплаты каяк-клуба")
public class BalanceController {
    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Operation(summary = "Получить список каякеров с краткими балансами")
    @GetMapping("/people/balances")
    public List<PersonBalanceSummaryView> peopleBalances() {
        return balanceService.listPersonBalances();
    }

    @Operation(summary = "Получить баланс каякера")
    @GetMapping("/people/{id}/balance")
    public BalanceView balance(@Parameter(description = "Идентификатор каякера") @PathVariable long id) {
        return balanceService.balance(id);
    }
}
