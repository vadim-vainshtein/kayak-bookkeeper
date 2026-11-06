package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.AttendanceReportStatus;
import kz.kayak.bookkeeper.api.dto.AttendanceReportView;
import kz.kayak.bookkeeper.service.AttendanceReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/attendance")
public class AttendanceReportController {
    private final AttendanceReportService reports;
    public AttendanceReportController(AttendanceReportService reports) { this.reports = reports; }

    @GetMapping
    @Operation(summary = "Получить отчёт по посещениям абонементов", description = "По умолчанию включает рассчитанные и завершённые абонементы. Повторяющийся subscriptionId ограничивает выбранные абонементы; status (FINALIZED или COMPLETED) дополнительно фильтрует их. Только начавшиеся тренировки участвуют в посещениях. Текущий состав, исторические посетители и гости показаны в матрице с сохранённой ролью каждого визита. Время определяется часовым поясом клуба; проценты посещаемости не рассчитываются, поскольку история состава не фиксируется.")
    @ApiResponse(responseCode = "400", description = "Некорректный статус или идентификатор")
    @ApiResponse(responseCode = "404", description = "Выбранный абонемент не найден")
    public AttendanceReportView attendanceReport(@RequestParam(required = false) List<Long> subscriptionId,
            @RequestParam(required = false) AttendanceReportStatus status) {
        return reports.attendanceReport(subscriptionId, status);
    }
}
