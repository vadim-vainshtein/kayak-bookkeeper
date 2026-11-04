package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.RateEndRequest;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.RateView;
import kz.kayak.bookkeeper.service.PricingRateService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Учёт тренировок", description = "Абонементы, посещения, тарифы и оплаты каяк-клуба")
public class PricingRateController {
    private final PricingRateService pricingRateService;

    public PricingRateController(PricingRateService pricingRateService) {
        this.pricingRateService = pricingRateService;
    }

    @Operation(summary = "Создать тариф", description = "Тариф действует с effectiveFrom; null в effectiveTo означает бессрочное действие.")
    @ApiResponse(responseCode = "409", description = "Период пересекается с существующим тарифом того же вида")
    @PostMapping("/pricing-rates")
    @ResponseStatus(HttpStatus.CREATED)
    public RateView createRate(@Valid @RequestBody RateRequest body) {
        return pricingRateService.createRate(body);
    }

    @Operation(summary = "Получить тарифы")
    @GetMapping("/pricing-rates")
    public List<RateView> rates() {
        return pricingRateService.listRates();
    }

    @Operation(summary = "Удалить тариф", description = "Удаляет тариф по идентификатору. Уже зафиксированные суммы в абонементах и начислениях не изменяются.")
    @ApiResponse(responseCode = "204", description = "Тариф удалён")
    @ApiResponse(responseCode = "404", description = "Тариф не найден")
    @DeleteMapping("/pricing-rates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRate(@Parameter(description = "Идентификатор тарифа") @PathVariable long id) {
        pricingRateService.deleteRate(id);
    }

    @Operation(summary = "Установить дату окончания тарифа")
    @ApiResponse(responseCode = "409", description = "Новая дата окончания создаёт пересечение тарифов")
    @PutMapping("/pricing-rates/{id}/effective-to")
    public RateView endRate(@Parameter(description = "Идентификатор тарифа") @PathVariable long id, @Valid @RequestBody RateEndRequest body) {
        return pricingRateService.endRate(id, body);
    }
}
