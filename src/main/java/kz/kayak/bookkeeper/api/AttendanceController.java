package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.kayak.bookkeeper.api.dto.AttendanceRequest;
import kz.kayak.bookkeeper.api.dto.AttendanceView;
import kz.kayak.bookkeeper.api.dto.DropInRequest;
import kz.kayak.bookkeeper.api.dto.RosterView;
import kz.kayak.bookkeeper.service.AttendanceService;
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
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "Отметить посещение участника абонемента")
    @ApiResponse(responseCode = "409", description = "Нельзя занять место, уже отданное разовому посетителю")
    @PutMapping("/sessions/{id}/attendance")
    public AttendanceView attendance(@Parameter(description = "Идентификатор тренировки") @PathVariable long id, @Valid @RequestBody AttendanceRequest body) {
        return attendanceService.markSubscriptionAttendance(id, body);
    }

    @Operation(summary = "Добавить разового посетителя", description = "Допускается только на место отмеченного отсутствующего участника.")
    @ApiResponse(responseCode = "409", description = "Нет места, освобождённого отмеченным отсутствующим участником")
    @PostMapping("/sessions/{id}/drop-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceView dropIn(@Parameter(description = "Идентификатор тренировки") @PathVariable long id, @Valid @RequestBody DropInRequest body) {
        return attendanceService.addDropIn(id, body);
    }

    @Operation(summary = "Получить перекличку тренировки")
    @GetMapping("/sessions/{id}/roster")
    public RosterView roster(@Parameter(description = "Идентификатор тренировки") @PathVariable long id) {
        return attendanceService.roster(id);
    }
}
