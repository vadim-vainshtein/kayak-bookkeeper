package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import kz.kayak.bookkeeper.api.dto.AttendanceUpdateRequest;
import kz.kayak.bookkeeper.api.dto.AttendanceView;
import kz.kayak.bookkeeper.service.AttendanceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions/{id}/attendance")
public class AttendanceController {
    private final AttendanceService attendance;
    public AttendanceController(AttendanceService attendance) { this.attendance = attendance; }

    @GetMapping
    @Operation(summary = "Получить посещения тренировки", description = "По умолчанию все отсутствуют. Возвращает участников, гостей и действующий сегодня тариф разовой тренировки; null означает отсутствие тарифа.")
    public AttendanceView get(@PathVariable long id) { return attendance.getAttendance(id); }

    @PutMapping
    @Operation(summary = "Сохранить посещения тренировки", description = "Заменяет список пришедших одной транзакцией. Доступно с начала тренировки рассчитанного или завершённого абонемента. Вместимость не ограничивает посещения. revision защищает от устаревшего сохранения; null amount использует сегодняшний тариф, 0 означает бесплатный визит. Для нового гостя вместо personId передайте fullName и необязательный phone.")
    public AttendanceView save(@PathVariable long id, @Valid @RequestBody AttendanceUpdateRequest body) {
        return attendance.saveAttendance(id, body);
    }
}
