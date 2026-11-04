package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.kayak.bookkeeper.api.dto.ChargeEditRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.service.ChargeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class ChargeController {
    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @Operation(summary = "Создать ручное начисление", description = "Создаёт редактируемое начисление для частного случая.")
    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeView createManualCharge(@Valid @RequestBody ManualChargeRequest body) {
        return chargeService.createManualCharge(body);
    }

    @Operation(summary = "Изменить начисление", description = "Нельзя уменьшить сумму ниже уже распределённых платежей.")
    @ApiResponse(responseCode = "409", description = "Новая сумма меньше уже распределённого платежа")
    @PutMapping("/charges/{id}")
    public ChargeView editCharge(@Parameter(description = "Идентификатор начисления") @PathVariable long id, @Valid @RequestBody ChargeEditRequest body) {
        return chargeService.editCharge(id, body);
    }

    @Operation(summary = "Удалить ошибочное начисление", description = "Удаляет начисление без распределённых оплат и обнуляет соответствующую сумму участника абонемента. Оплаты сохраняются.")
    @ApiResponse(responseCode = "204", description = "Начисление удалено")
    @ApiResponse(responseCode = "404", description = "Начисление не найдено")
    @ApiResponse(responseCode = "409", description = "Сначала требуется отменить распределение оплаты")
    @DeleteMapping("/charges/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCharge(@Parameter(description = "Идентификатор начисления") @PathVariable long id) {
        chargeService.deleteCharge(id);
    }
}
