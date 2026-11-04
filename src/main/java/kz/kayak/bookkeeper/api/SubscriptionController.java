package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.DraftSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionEditPreviewView;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.RecurringSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.RecurringSubscriptionWithMembersRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionMetadataUpdateRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.service.FinalizedSubscriptionEditService;
import kz.kayak.bookkeeper.service.SubscriptionFinalizationService;
import kz.kayak.bookkeeper.service.SubscriptionQueryService;
import kz.kayak.bookkeeper.service.SubscriptionService;
import org.springframework.http.HttpStatus;
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
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final SubscriptionQueryService subscriptionQueryService;
    private final FinalizedSubscriptionEditService finalizedSubscriptionEditService;
    private final SubscriptionFinalizationService subscriptionFinalizationService;

    public SubscriptionController(
            SubscriptionService subscriptionService,
            SubscriptionQueryService subscriptionQueryService,
            FinalizedSubscriptionEditService finalizedSubscriptionEditService,
            SubscriptionFinalizationService subscriptionFinalizationService) {
        this.subscriptionService = subscriptionService;
        this.subscriptionQueryService = subscriptionQueryService;
        this.finalizedSubscriptionEditService = finalizedSubscriptionEditService;
        this.subscriptionFinalizationService = subscriptionFinalizationService;
    }

    @Operation(summary = "Создать абонемент по списку тренировок", description = "Принимает явный список дат и времени; даты должны быть уникальны. Вместимость по умолчанию 8; title и description необязательны.")
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView createSubscription(@Valid @RequestBody ExplicitSubscriptionRequest body) {
        return subscriptionService.createExplicitSubscription(body);
    }

    @Operation(summary = "Создать абонемент по еженедельному расписанию", description = "Вычисляет next-or-same дату указанного дня недели и последующие еженедельные тренировки. title и description необязательны.")
    @PostMapping("/subscriptions/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView createRecurringSubscription(@Valid @RequestBody RecurringSubscriptionRequest body) {
        return subscriptionService.createRecurringSubscription(body);
    }

    @Operation(summary = "Создать еженедельный абонемент с участниками", description = "Расписание и весь список участников сохраняются одной транзакцией.")
    @PostMapping("/subscriptions/recurring-with-members")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView createRecurringSubscriptionWithMembers(@Valid @RequestBody RecurringSubscriptionWithMembersRequest body) {
        return subscriptionService.createRecurringSubscriptionWithMembers(body);
    }

    @Operation(summary = "Получить все абонементы", description = "Возвращает полные карточки абонементов в порядке создания (по возрастанию идентификатора).")
    @GetMapping("/subscriptions")
    public List<SubscriptionView> subscriptions() {
        return subscriptionQueryService.listSubscriptions();
    }

    @Operation(summary = "Получить абонемент")
    @GetMapping("/subscriptions/{id}")
    public SubscriptionView subscription(@Parameter(description = "Идентификатор абонемента") @PathVariable long id) {
        return subscriptionQueryService.getSubscription(id);
    }

    @Operation(summary = "Изменить название и описание абонемента", description = "Доступно при любом статусе. Расписание, состав участников, вместимость и рассчитанные начисления не изменяются.")
    @ApiResponse(responseCode = "404", description = "Абонемент не найден")
    @PutMapping("/subscriptions/{id}/metadata")
    public SubscriptionView updateSubscriptionMetadata(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody SubscriptionMetadataUpdateRequest body) {
        return subscriptionService.updateSubscriptionMetadata(id, body);
    }

    @Operation(summary = "Изменить расписание и состав черновика абонемента", description = "Заменяет все даты тренировок и участников одной транзакцией. Доступно только до расчёта абонемента.")
    @ApiResponse(responseCode = "409", description = "Абонемент уже рассчитан или данные черновика некорректны")
    @PutMapping("/subscriptions/{id}/draft")
    public SubscriptionView updateDraftSubscription(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody DraftSubscriptionUpdateRequest body) {
        return subscriptionService.updateDraftSubscription(id, body);
    }

    @Operation(summary = "Проверить влияние полного редактирования рассчитанного абонемента", description = "Возвращает распределённые оплаты, которые будут затронуты пересчётом. Данные не изменяет.")
    @PostMapping("/subscriptions/{id}/finalized-edit-preview")
    public FinalizedSubscriptionEditPreviewView previewFinalizedSubscriptionEdit(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody FinalizedSubscriptionUpdateRequest body) {
        return finalizedSubscriptionEditService.previewFinalizedSubscriptionEdit(id, body);
    }

    @Operation(summary = "Полностью изменить рассчитанный абонемент", description = "Изменяет расписание, участников, вместимость и метаданные. При затронутых распределённых оплатах требует явного подтверждения.")
    @ApiResponse(responseCode = "409", description = "Нужно подтвердить затронутые распределённые оплаты либо абонемент уже завершён")
    @PutMapping("/subscriptions/{id}/finalized")
    public SubscriptionView updateFinalizedSubscription(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody FinalizedSubscriptionUpdateRequest body) {
        return finalizedSubscriptionEditService.updateFinalizedSubscription(id, body);
    }

    @Operation(summary = "Добавить участника в абонемент", description = "Можно добавить не более вместимости абонемента.")
    @ApiResponse(responseCode = "409", description = "Абонемент закрыт или вместимость исчерпана")
    @PostMapping("/subscriptions/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView addMember(@Parameter(description = "Идентификатор абонемента") @PathVariable long id, @Valid @RequestBody MemberRequest body) {
        return subscriptionService.addMember(id, body);
    }

    @Operation(summary = "Финализировать абонемент", description = "Фиксирует цены, рассчитывает долю бассейна с округлением вверх и создаёт начисления.")
    @ApiResponse(responseCode = "409", description = "Абонемент нельзя финализировать или отсутствует обязательный тариф")
    @PostMapping("/subscriptions/{id}/finalize")
    public SubscriptionView finalize(@Parameter(description = "Идентификатор абонемента") @PathVariable long id) {
        return subscriptionFinalizationService.finalizeSubscription(id);
    }

    @Operation(summary = "Завершить абонемент")
    @PostMapping("/subscriptions/{id}/complete")
    public SubscriptionView complete(@Parameter(description = "Идентификатор абонемента") @PathVariable long id) {
        return subscriptionService.completeSubscription(id);
    }
}
