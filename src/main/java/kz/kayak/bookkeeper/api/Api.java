package kz.kayak.bookkeeper.api;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import kz.kayak.bookkeeper.domain.*;
import java.time.*;
import java.util.List;

public final class Api {
    private Api() { }
    @Schema(description = "Данные нового каякера")
    public record PersonRequest(@Schema(description = "Полное имя", example = "Алия Абдрахманова") @NotBlank String fullName,
                                @Schema(description = "Номер телефона", example = "+77011234567") String phone,
                                @Schema(description = "Внутренняя заметка") String note) { }
    @Schema(description = "Обновление карточки каякера")
    public record PersonUpdateRequest(@Schema(description = "Полное имя", example = "Алия Абдрахманова") @NotBlank String fullName,
                                      @Schema(description = "Номер телефона", example = "+77011234567") String phone,
                                      @Schema(description = "Внутренняя заметка") String note,
                                      @Schema(description = "Активен ли каякер; неактивного нельзя добавить в новый абонемент", example = "true") @NotNull Boolean active) { }
    public record PersonView(Long id, String fullName, String phone, String note, boolean active) { }
    @Schema(description = "Краткий баланс каякера для списка")
    public record PersonBalanceSummaryView(Long personId, String fullName, boolean active, long charged, long paid, long balance) { }
    @Schema(description = "Смена пароля текущего пользователя")
    public record PasswordChangeRequest(@NotBlank @Schema(description = "Текущий пароль") String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) @Schema(description = "Новый пароль, от 8 до 72 символов") String newPassword) { }
    @Schema(description = "Тариф за одну тренировку с периодом действия")
    public record RateRequest(@Schema(description = "Вид тарифа", example = "POOL_RENTAL") @NotNull RateCode code,
                              @Schema(description = "Сумма в тенге", example = "30000") @PositiveOrZero long amount,
                              @Schema(description = "Дата начала действия", example = "2026-11-01") @NotNull LocalDate effectiveFrom,
                              @Schema(description = "Дата окончания; null — бессрочно", nullable = true) LocalDate effectiveTo) { }
    public record RateView(Long id, RateCode code, long amount, LocalDate effectiveFrom, LocalDate effectiveTo) { }
    public record RateEndRequest(@NotNull LocalDate effectiveTo) { }
    @Schema(description = "Абонемент с перечисленными датами и временем тренировок")
    public record ExplicitSubscriptionRequest(@Schema(description = "Даты и время тренировок", example = "[\"2026-11-10T19:00:00\", \"2026-11-17T19:00:00\"]") @NotEmpty List<@NotNull LocalDateTime> startsAt,
                                              @Schema(description = "Вместимость, от 1 до 8; по умолчанию 8", example = "8") @Min(1) @Max(8) Integer capacity,
                                              @Schema(description = "Необязательное название абонемента", example = "Ноябрь, вторник") @Size(max = 255) String title,
                                              @Schema(description = "Необязательное описание абонемента") @Size(max = 1000) String description) {
        public ExplicitSubscriptionRequest(List<LocalDateTime> startsAt, Integer capacity) { this(startsAt, capacity, null, null); }
    }
    @Schema(description = "Редактирование названия и описания абонемента. Не изменяет расписание, участников, вместимость или зафиксированные начисления.")
    public record SubscriptionMetadataUpdateRequest(@Schema(description = "Необязательное название", example = "Ноябрь, вторник") @Size(max = 255) String title,
                                                    @Schema(description = "Необязательное описание") @Size(max = 1000) String description) { }
    @Schema(description = "Полная замена расписания и состава участников черновика абонемента")
    public record DraftSubscriptionUpdateRequest(@NotEmpty List<@NotNull LocalDateTime> startsAt,
                                                 @NotEmpty @Size(max = 8) List<@jakarta.validation.Valid MemberRequest> members,
                                                 @Min(1) @Max(8) Integer capacity,
                                                 @Size(max = 255) String title,
                                                 @Size(max = 1000) String description) { }
    @Schema(description = "Полное редактирование рассчитанного, но ещё не завершённого абонемента")
    public record FinalizedSubscriptionUpdateRequest(@NotEmpty List<@NotNull LocalDateTime> startsAt,
                                                     @NotEmpty @Size(max = 8) List<@jakarta.validation.Valid MemberRequest> members,
                                                     @NotNull @Min(1) @Max(8) Integer capacity,
                                                     @Size(max = 255) String title,
                                                     @Size(max = 1000) String description,
                                                     @Schema(description = "Подтверждает изменение начислений с уже распределёнными оплатами") boolean confirmAllocatedPayments) { }
    @Schema(description = "Распределённая оплата, которую затрагивает редактирование рассчитанного абонемента")
    public record AllocatedPaymentWarningView(Long paymentId, LocalDate paidAt, long paymentAmount, long allocatedAmount,
                                              Long chargeId, String chargeDescription) { }
    public record FinalizedSubscriptionEditPreviewView(boolean requiresConfirmation,
                                                       List<AllocatedPaymentWarningView> allocatedPayments) { }
    @Schema(description = "Абонемент с еженедельным расписанием")
    public record RecurringSubscriptionRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
                                               @Schema(description = "Дата начала расчёта; при совпадении с dayOfWeek включается в список", example = "2026-11-10") @NotNull LocalDate startDate,
                                               @Positive int sessionCount,
                                               @Schema(description = "Вместимость, от 1 до 8; по умолчанию 8", example = "8") @Min(1) @Max(8) Integer capacity,
                                               @Schema(description = "Необязательное название абонемента", example = "Ноябрь, вторник") @Size(max = 255) String title,
                                               @Schema(description = "Необязательное описание абонемента") @Size(max = 1000) String description) { }
    @Schema(description = "Еженедельный абонемент с участниками; сохраняется одной транзакцией")
    public record RecurringSubscriptionWithMembersRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
                                                          @NotNull LocalDate startDate, @Positive int sessionCount,
                                                          @Min(1) @Max(8) Integer capacity, @Size(max = 255) String title,
                                                          @Size(max = 1000) String description,
                                                          @NotNull @Size(min = 1, max = 8) List<@jakarta.validation.Valid MemberRequest> members) { }
    @Schema(description = "Участник абонемента и используемое клубное снаряжение")
    public record MemberRequest(@NotNull Long personId, boolean usesSkirt, boolean usesPaddle, boolean usesKayak) { }
    public record MemberView(Long id, Long personId, String personName, boolean usesSkirt, boolean usesPaddle, boolean usesKayak, Long poolShare, Long skirtFee, Long paddleFee, Long kayakFee) { }
    public record SessionView(Long id, LocalDateTime startsAt) { }
    public record SubscriptionView(Long id, int capacity, String title, String description, SubscriptionStatus status, Long poolRentalAmount, List<MemberView> members, List<SessionView> sessions) { }
    @Schema(description = "Отметка переклички участника абонемента")
    public record AttendanceRequest(@NotNull Long personId, @Schema(example = "PRESENT") @NotNull AttendanceStatus status) { }
    public record DropInRequest(@NotNull Long personId) { }
    public record AttendanceView(Long id, Long personId, String personName, AttendanceType type, AttendanceStatus status) { }
    public record RosterView(Long sessionId, int capacity, long vacanciesForDropIns, List<AttendanceView> attendance) { }
    @Schema(description = "Полученный платёж")
    public record PaymentRequest(@NotNull Long personId, @Schema(description = "Сумма в тенге", example = "15000") @Positive long amount, @NotNull LocalDate paidAt, @NotNull PaymentMethod method, String note) { }
    public record PaymentAllocationView(Long id, Long chargeId, String chargeDescription, long amount) { }
    public record PaymentView(Long id, Long personId, long amount, LocalDate paidAt, PaymentMethod method, String note, List<PaymentAllocationView> allocations) { }
    public record AllocationRequest(@NotNull Long chargeId, @Positive long amount) { }
    @Schema(description = "Платёж и необязательное распределение на одно начисление; сохраняются одной транзакцией")
    public record PaymentWithAllocationRequest(@NotNull Long personId, @Positive long amount, @NotNull LocalDate paidAt,
                                               @NotNull PaymentMethod method, String note, Long chargeId,
                                               @Positive Long allocationAmount) { }
    @Schema(description = "Ручное начисление для частного случая")
    public record ManualChargeRequest(@NotNull Long personId, @Positive long amount, @NotNull LocalDate chargedAt, @NotBlank String description, Long subscriptionId) { }
    public record ChargeEditRequest(@Positive long amount, @NotNull LocalDate chargedAt, String description) { }
    public record ChargeView(Long id, ChargeType type, long amount, long allocated, LocalDate chargedAt, String description, Long subscriptionId, Long attendanceId) { }
    public record BalanceView(Long personId, long charged, long paid, long balance, List<ChargeView> charges, List<PaymentView> payments) { }
    @Schema(description = "Сводные показатели финансового отчёта за выбранный период")
    public record FinancialReportSummaryView(long chargedInPeriod, long receivedInPeriod, long debtAtEnd,
                                             long creditAtEnd, long unallocatedPaymentsAtEnd,
                                             long allocatedFundsAtEnd, long poolRentalCostAtEnd,
                                             long clubProfitAtEnd) { }
    @Schema(description = "Финансовые показатели одного абонемента")
    public record SubscriptionFinancialReportView(Long subscriptionId, String title, SubscriptionStatus status,
                                                  LocalDate firstSessionDate, LocalDate lastSessionDate, int memberCount,
                                                  long chargedInPeriod, long allocatedPaymentsInPeriod, long outstandingAtEnd,
                                                  long allocatedPaymentsAtEnd, long poolRentalCostAtEnd,
                                                  long clubProfitAtEnd) { }
    @Schema(description = "Финансовые показатели каякера")
    public record PersonFinancialReportView(Long personId, String fullName, boolean active, long chargedInPeriod,
                                            long receivedInPeriod, long balanceAtEnd, LocalDate lastPaymentDate) { }
    @Schema(description = "Финансовый отчёт: начисления учитываются по chargedAt, оплаты — по paidAt; баланс рассчитан на конец периода")
    public record FinancialReportView(LocalDate from, LocalDate to, FinancialReportSummaryView summary,
                                      List<SubscriptionFinancialReportView> subscriptions,
                                      List<PersonFinancialReportView> people) { }
}
