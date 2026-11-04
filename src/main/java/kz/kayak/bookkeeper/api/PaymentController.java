package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.PaymentAmountRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PaymentWithAllocationRequest;
import kz.kayak.bookkeeper.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Учёт тренировок", description = "Абонементы, посещения, тарифы и оплаты каяк-клуба")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Зарегистрировать платёж")
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView payment(@Valid @RequestBody PaymentRequest body) {
        return paymentService.createPayment(body);
    }

    @Operation(summary = "Зарегистрировать платёж и распределить его одной операцией")
    @PostMapping("/payments/with-allocation")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView paymentWithAllocation(@Valid @RequestBody PaymentWithAllocationRequest body) {
        return paymentService.createPaymentWithAllocation(body);
    }

    @Operation(summary = "Распределить платёж по начислению")
    @PostMapping("/payments/{id}/allocations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void allocate(@Parameter(description = "Идентификатор платежа") @PathVariable long id, @Valid @RequestBody AllocationRequest body) {
        paymentService.allocatePayment(id, body);
    }

    @Operation(summary = "Изменить сумму, дату и комментарий платежа", responses = {
            @ApiResponse(responseCode = "200", description = "Обновлённый платёж"),
            @ApiResponse(responseCode = "400", description = "Некорректная сумма, дата или комментарий длиннее 1000 символов"),
            @ApiResponse(responseCode = "404", description = "Платёж не найден"),
            @ApiResponse(responseCode = "409", description = "Сумма меньше уже распределённой суммы")
    })
    @PatchMapping("/payments/{id}")
    public PaymentView editAmount(@PathVariable long id, @Valid @RequestBody PaymentAmountRequest body) {
        return paymentService.editPaymentAmount(id, body);
    }

    @Operation(summary = "Отменить распределение платежа", responses = {
            @ApiResponse(responseCode = "204", description = "Распределение отменено, сумма снова доступна для распределения"),
            @ApiResponse(responseCode = "404", description = "Платёж или его распределение не найдены")
    })
    @DeleteMapping("/payments/{paymentId}/allocations/{allocationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelAllocation(@PathVariable long paymentId, @PathVariable long allocationId) {
        paymentService.cancelAllocation(paymentId, allocationId);
    }
}
