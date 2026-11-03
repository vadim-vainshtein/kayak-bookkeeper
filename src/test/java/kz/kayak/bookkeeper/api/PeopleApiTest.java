package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.service.BookkeepingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PeopleApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46YWRtaW4=";
    @Autowired private MockMvc mockMvc;
    @Autowired private BookkeepingService service;

    @Test
    void updatesKayakerAndAllowsDeactivation() throws Exception {
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Старая Алия", "+77010000000", "старое"));

        mockMvc.perform(put("/api/people/{id}", person.id())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Новая Алия\",\"phone\":\"+77011234567\",\"note\":\"обновлено\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(person.id()))
                .andExpect(jsonPath("$.fullName").value("Новая Алия"))
                .andExpect(jsonPath("$.phone").value("+77011234567"))
                .andExpect(jsonPath("$.note").value("обновлено"))
                .andExpect(jsonPath("$.active").value(false));

        assertThat(service.listPeople()).singleElement().satisfies(updated -> assertThat(updated.active()).isFalse());
    }

    @Test
    void returnsNotFoundForUnknownKayaker() throws Exception {
        mockMvc.perform(put("/api/people/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Алия\",\"active\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Person 999999 was not found"));
    }

    @Test
    void rejectsBlankFullName() throws Exception {
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Алия", null, null));

        mockMvc.perform(put("/api/people/{id}", person.id())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsPeopleWithTheirBalanceSummaries() throws Exception {
        Api.PersonView person = service.createPerson(new Api.PersonRequest("Алия", null, null));
        service.createManualCharge(new Api.ManualChargeRequest(person.id(), 5_000, java.time.LocalDate.of(2026, 11, 1), "Тренировка", null));
        Api.ChargeView charge = service.balance(person.id()).charges().getFirst();
        Api.PaymentView partlyAllocated = service.createPayment(new Api.PaymentRequest(person.id(), 2_000, java.time.LocalDate.of(2026, 11, 2), kz.kayak.bookkeeper.domain.PaymentMethod.CASH, null));
        service.allocatePayment(partlyAllocated.id(), new Api.AllocationRequest(charge.id(), 1_200));
        Api.PaymentView fullyAllocated = service.createPayment(new Api.PaymentRequest(person.id(), 500, java.time.LocalDate.of(2026, 11, 3), kz.kayak.bookkeeper.domain.PaymentMethod.CASH, null));
        service.allocatePayment(fullyAllocated.id(), new Api.AllocationRequest(charge.id(), 500));

        mockMvc.perform(get("/api/people/balances").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personId").value(person.id()))
                .andExpect(jsonPath("$[0].fullName").value("Алия"))
                .andExpect(jsonPath("$[0].charged").value(5_000))
                .andExpect(jsonPath("$[0].paid").value(2_500))
                .andExpect(jsonPath("$[0].unallocated").value(800))
                .andExpect(jsonPath("$[0].balance").value(2_500));
    }

    @Test
    void listsKayakersAlphabeticallyByName() throws Exception {
        service.createPerson(new Api.PersonRequest("Зоя", null, null));
        service.createPerson(new Api.PersonRequest("алия", null, null));
        service.createPerson(new Api.PersonRequest("Алина", null, null));
        service.createPerson(new Api.PersonRequest("Алия", null, null));
        service.createPerson(new Api.PersonRequest("Борис", null, null));

        mockMvc.perform(get("/api/people").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Алина"))
                .andExpect(jsonPath("$[1].fullName").value("алия"))
                .andExpect(jsonPath("$[2].fullName").value("Алия"))
                .andExpect(jsonPath("$[3].fullName").value("Борис"))
                .andExpect(jsonPath("$[4].fullName").value("Зоя"));

        mockMvc.perform(get("/api/people/balances").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Алина"))
                .andExpect(jsonPath("$[1].fullName").value("алия"))
                .andExpect(jsonPath("$[2].fullName").value("Алия"))
                .andExpect(jsonPath("$[3].fullName").value("Борис"))
                .andExpect(jsonPath("$[4].fullName").value("Зоя"));
    }
}
