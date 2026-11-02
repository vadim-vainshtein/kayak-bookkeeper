package kz.kayak.bookkeeper.api;

import jakarta.validation.Valid;
import kz.kayak.bookkeeper.service.BookkeepingService;
import kz.kayak.bookkeeper.security.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Учёт тренировок", description = "Абонементы, посещения, тарифы и оплаты каяк-клуба")
public class BookkeepingController {
    private final BookkeepingService service;
    private final AccountService accountService;
    public BookkeepingController(BookkeepingService service, AccountService accountService) { this.service = service; this.accountService = accountService; }
    @Operation(summary = "Сменить пароль текущего пользователя")
    @PutMapping("/account/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void changePassword(Authentication authentication, @Valid @RequestBody Api.PasswordChangeRequest body) { accountService.changePassword(authentication.getName(), body.currentPassword(), body.newPassword()); }
    @Operation(summary = "Добавить каякера")
    @PostMapping("/people") @ResponseStatus(HttpStatus.CREATED) public Api.PersonView createPerson(@Valid @RequestBody Api.PersonRequest body) { return service.createPerson(body); }
    @Operation(summary = "Получить список каякеров")
    @GetMapping("/people") public List<Api.PersonView> people() { return service.listPeople(); }
    @Operation(summary = "Получить список каякеров с краткими балансами")
    @GetMapping("/people/balances") public List<Api.PersonBalanceSummaryView> peopleBalances() { return service.listPersonBalances(); }
    @Operation(summary = "Изменить карточку каякера", description = "Позволяет изменить контактные данные, заметку и признак активности. Уже существующие абонементы, начисления и платежи сохраняются.")
    @ApiResponse(responseCode = "404", description = "Каякер не найден")
    @PutMapping("/people/{id}") public Api.PersonView updatePerson(@Parameter(description = "Идентификатор каякера") @PathVariable long id, @Valid @RequestBody Api.PersonUpdateRequest body) { return service.updatePerson(id, body); }
    @Operation(summary = "Создать тариф", description = "Тариф действует с effectiveFrom; null в effectiveTo означает бессрочное действие.")
    @ApiResponse(responseCode = "409", description = "Период пересекается с существующим тарифом того же вида")
    @PostMapping("/pricing-rates") @ResponseStatus(HttpStatus.CREATED) public Api.RateView createRate(@Valid @RequestBody Api.RateRequest body) { return service.createRate(body); }
    @Operation(summary = "Получить тарифы")
    @GetMapping("/pricing-rates") public List<Api.RateView> rates() { return service.listRates(); }
    @Operation(summary = "Удалить тариф", description = "Удаляет тариф по идентификатору. Уже зафиксированные суммы в абонементах и начислениях не изменяются.")
    @ApiResponse(responseCode = "204", description = "Тариф удалён")
    @ApiResponse(responseCode = "404", description = "Тариф не найден")
    @DeleteMapping("/pricing-rates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteRate(@Parameter(description = "Идентификатор тарифа") @PathVariable long id) { service.deleteRate(id); }
    @Operation(summary = "Установить дату окончания тарифа")
    @ApiResponse(responseCode = "409", description = "Новая дата окончания создаёт пересечение тарифов")
    @PutMapping("/pricing-rates/{id}/effective-to") public Api.RateView endRate(@Parameter(description = "Идентификатор тарифа") @PathVariable long id, @Valid @RequestBody Api.RateEndRequest body) { return service.endRate(id, body); }
    @Operation(summary = "Создать абонемент по списку тренировок", description = "Принимает явный список дат и времени; даты должны быть уникальны. Вместимость по умолчанию 8; title и description необязательны.")
    @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.CREATED) public Api.SubscriptionView createSubscription(@Valid @RequestBody Api.ExplicitSubscriptionRequest body) { return service.createExplicitSubscription(body); }
    @Operation(summary = "Создать абонемент по еженедельному расписанию", description = "Вычисляет next-or-same дату указанного дня недели и последующие еженедельные тренировки. title и description необязательны.")
    @PostMapping("/subscriptions/recurring") @ResponseStatus(HttpStatus.CREATED) public Api.SubscriptionView createRecurringSubscription(@Valid @RequestBody Api.RecurringSubscriptionRequest body) { return service.createRecurringSubscription(body); }
    @Operation(summary = "Создать еженедельный абонемент с участниками", description = "Расписание и весь список участников сохраняются одной транзакцией.")
    @PostMapping("/subscriptions/recurring-with-members") @ResponseStatus(HttpStatus.CREATED) public Api.SubscriptionView createRecurringSubscriptionWithMembers(@Valid @RequestBody Api.RecurringSubscriptionWithMembersRequest body) { return service.createRecurringSubscriptionWithMembers(body); }
    @Operation(summary = "Получить все абонементы", description = "Возвращает полные карточки абонементов в порядке создания (по возрастанию идентификатора).")
    @GetMapping("/subscriptions") public List<Api.SubscriptionView> subscriptions() { return service.listSubscriptions(); }
    @Operation(summary = "Получить абонемент")
    @GetMapping("/subscriptions/{id}") public Api.SubscriptionView subscription(@Parameter(description = "Идентификатор абонемента") @PathVariable long id) { return service.getSubscription(id); }
    @Operation(summary = "Изменить название и описание абонемента", description = "Доступно при любом статусе. Расписание, состав участников, вместимость и рассчитанные начисления не изменяются.")
    @ApiResponse(responseCode = "404", description = "Абонемент не найден")
    @PutMapping("/subscriptions/{id}/metadata") public Api.SubscriptionView updateSubscriptionMetadata(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody Api.SubscriptionMetadataUpdateRequest body) { return service.updateSubscriptionMetadata(id, body); }
    @Operation(summary = "Изменить расписание и состав черновика абонемента", description = "Заменяет все даты тренировок и участников одной транзакцией. Доступно только до расчёта абонемента.")
    @ApiResponse(responseCode = "409", description = "Абонемент уже рассчитан или данные черновика некорректны")
    @PutMapping("/subscriptions/{id}/draft") public Api.SubscriptionView updateDraftSubscription(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody Api.DraftSubscriptionUpdateRequest body) { return service.updateDraftSubscription(id, body); }
    @Operation(summary = "Проверить влияние полного редактирования рассчитанного абонемента", description = "Возвращает распределённые оплаты, которые будут затронуты пересчётом. Данные не изменяет.")
    @PostMapping("/subscriptions/{id}/finalized-edit-preview") public Api.FinalizedSubscriptionEditPreviewView previewFinalizedSubscriptionEdit(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody Api.FinalizedSubscriptionUpdateRequest body) { return service.previewFinalizedSubscriptionEdit(id, body); }
    @Operation(summary = "Полностью изменить рассчитанный абонемент", description = "Изменяет расписание, участников, вместимость и метаданные. При затронутых распределённых оплатах требует явного подтверждения.")
    @ApiResponse(responseCode = "409", description = "Нужно подтвердить затронутые распределённые оплаты либо абонемент уже завершён")
    @PutMapping("/subscriptions/{id}/finalized") public Api.SubscriptionView updateFinalizedSubscription(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody Api.FinalizedSubscriptionUpdateRequest body) { return service.updateFinalizedSubscription(id, body); }
    @Operation(summary = "Добавить участника в абонемент", description = "Можно добавить не более вместимости абонемента.")
    @ApiResponse(responseCode = "409", description = "Абонемент закрыт или вместимость исчерпана")
    @PostMapping("/subscriptions/{id}/members") @ResponseStatus(HttpStatus.CREATED) public Api.SubscriptionView addMember(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody Api.MemberRequest body) { return service.addMember(id, body); }
    @Operation(summary = "Финализировать абонемент", description = "Фиксирует цены, рассчитывает долю бассейна с округлением вверх и создаёт начисления.")
    @ApiResponse(responseCode = "409", description = "Абонемент нельзя финализировать или отсутствует обязательный тариф")
    @PostMapping("/subscriptions/{id}/finalize") public Api.SubscriptionView finalize(@Parameter(description = "Идентификатор абонемента") @PathVariable long id) { return service.finalizeSubscription(id); }
    @Operation(summary = "Завершить абонемент")
    @PostMapping("/subscriptions/{id}/complete") public Api.SubscriptionView complete(@Parameter(description = "Идентификатор абонемента") @PathVariable long id) { return service.completeSubscription(id); }
    @Operation(summary = "Отметить посещение участника абонемента")
    @ApiResponse(responseCode = "409", description = "Нельзя занять место, уже отданное разовому посетителю")
    @PutMapping("/sessions/{id}/attendance") public Api.AttendanceView attendance(@Parameter(description = "Идентификатор тренировки") @PathVariable long id, @Valid @RequestBody Api.AttendanceRequest body) { return service.markSubscriptionAttendance(id, body); }
    @Operation(summary = "Добавить разового посетителя", description = "Допускается только на место отмеченного отсутствующего участника.")
    @ApiResponse(responseCode = "409", description = "Нет места, освобождённого отмеченным отсутствующим участником")
    @PostMapping("/sessions/{id}/drop-ins") @ResponseStatus(HttpStatus.CREATED) public Api.AttendanceView dropIn(@Parameter(description = "Идентификатор тренировки") @PathVariable long id, @Valid @RequestBody Api.DropInRequest body) { return service.addDropIn(id, body); }
    @Operation(summary = "Получить перекличку тренировки")
    @GetMapping("/sessions/{id}/roster") public Api.RosterView roster(@Parameter(description = "Идентификатор тренировки") @PathVariable long id) { return service.roster(id); }
    @Operation(summary = "Создать ручное начисление", description = "Создаёт редактируемое начисление для частного случая.")
    @PostMapping("/charges") @ResponseStatus(HttpStatus.CREATED) public Api.ChargeView createManualCharge(@Valid @RequestBody Api.ManualChargeRequest body) { return service.createManualCharge(body); }
    @Operation(summary = "Изменить начисление", description = "Нельзя уменьшить сумму ниже уже распределённых платежей.")
    @ApiResponse(responseCode = "409", description = "Новая сумма меньше уже распределённого платежа")
    @PutMapping("/charges/{id}") public Api.ChargeView editCharge(@Parameter(description = "Идентификатор начисления") @PathVariable long id, @Valid @RequestBody Api.ChargeEditRequest body) { return service.editCharge(id, body); }
    @Operation(summary = "Зарегистрировать платёж")
    @PostMapping("/payments") @ResponseStatus(HttpStatus.CREATED) public Api.PaymentView payment(@Valid @RequestBody Api.PaymentRequest body) { return service.createPayment(body); }
    @Operation(summary = "Зарегистрировать платёж и распределить его одной операцией")
    @PostMapping("/payments/with-allocation") @ResponseStatus(HttpStatus.CREATED) public Api.PaymentView paymentWithAllocation(@Valid @RequestBody Api.PaymentWithAllocationRequest body) { return service.createPaymentWithAllocation(body); }
    @Operation(summary = "Распределить платёж по начислению")
    @PostMapping("/payments/{id}/allocations") @ResponseStatus(HttpStatus.NO_CONTENT) public void allocate(@Parameter(description = "Идентификатор платежа") @PathVariable long id, @Valid @RequestBody Api.AllocationRequest body) { service.allocatePayment(id, body); }
    @Operation(summary = "Получить баланс каякера")
    @GetMapping("/people/{id}/balance") public Api.BalanceView balance(@Parameter(description = "Идентификатор каякера") @PathVariable long id) { return service.balance(id); }
}
