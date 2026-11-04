package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonUpdateRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.service.PersonService;
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
public class PersonController {
    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @Operation(summary = "Добавить каякера")
    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonView createPerson(@Valid @RequestBody PersonRequest body) {
        return personService.createPerson(body);
    }

    @Operation(summary = "Получить список каякеров")
    @GetMapping("/people")
    public List<PersonView> people() {
        return personService.listPeople();
    }

    @Operation(summary = "Изменить карточку каякера", description = "Позволяет изменить контактные данные, заметку и признак активности. Уже существующие абонементы, начисления и платежи сохраняются.")
    @ApiResponse(responseCode = "404", description = "Каякер не найден")
    @PutMapping("/people/{id}")
    public PersonView updatePerson(@Parameter(description = "Идентификатор каякера") @PathVariable long id, @Valid @RequestBody PersonUpdateRequest body) {
        return personService.updatePerson(id, body);
    }
}
