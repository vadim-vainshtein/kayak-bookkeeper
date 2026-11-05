package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.api.dto.AllocationRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.api.dto.PaymentRequest;
import kz.kayak.bookkeeper.api.dto.PaymentView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.service.BalanceService;
import kz.kayak.bookkeeper.service.ChargeService;
import kz.kayak.bookkeeper.service.PaymentService;
import kz.kayak.bookkeeper.service.PersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired private MockMvc mockMvc;
    @Autowired BalanceService balanceService;
    @Autowired ChargeService chargeService;
    @Autowired PaymentService paymentService;
    @Autowired PersonService personService;

    @Test
    void updatesKayakerAndAllowsDeactivation() throws Exception {
        PersonView person = personService.createPerson(new PersonRequest("Старая Алия", "+77010000000", "старое"));

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

        assertThat(personService.listPeople()).singleElement().satisfies(updated -> assertThat(updated.active()).isFalse());
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
        PersonView person = personService.createPerson(new PersonRequest("Алия", null, null));

        mockMvc.perform(put("/api/people/{id}", person.id())
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsPeopleWithTheirBalanceSummaries() throws Exception {
        PersonView person = personService.createPerson(new PersonRequest("Алия", null, null));
        chargeService.createManualCharge(new ManualChargeRequest(person.id(), 5_000, java.time.LocalDate.of(2026, 11, 1), "Тренировка", null));
        ChargeView charge = balanceService.balance(person.id()).charges().getFirst();
        PaymentView partlyAllocated = paymentService.createPayment(new PaymentRequest(person.id(), 2_000, java.time.LocalDate.of(2026, 11, 2), kz.kayak.bookkeeper.domain.PaymentMethod.CASH, null));
        paymentService.allocatePayment(partlyAllocated.id(), new AllocationRequest(charge.id(), 1_200));
        PaymentView fullyAllocated = paymentService.createPayment(new PaymentRequest(person.id(), 500, java.time.LocalDate.of(2026, 11, 3), kz.kayak.bookkeeper.domain.PaymentMethod.CASH, null));
        paymentService.allocatePayment(fullyAllocated.id(), new AllocationRequest(charge.id(), 500));

        mockMvc.perform(get("/api/people/balances").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personId").value(person.id()))
                .andExpect(jsonPath("$[0].fullName").value("Алия"))
                .andExpect(jsonPath("$[0].charged").value(5_000))
                .andExpect(jsonPath("$[0].paid").value(2_500))
                .andExpect(jsonPath("$[0].unallocated").value(800))
                .andExpect(jsonPath("$[0].balance").value(2_500))
                .andExpect(jsonPath("$[0].subscriptionBalances[0].subscriptionId").isEmpty())
                .andExpect(jsonPath("$[0].subscriptionBalances[0].charged").value(5_000))
                .andExpect(jsonPath("$[0].subscriptionBalances[0].allocated").value(1_700))
                .andExpect(jsonPath("$[0].subscriptionBalances[0].balance").value(3_300));
    }

    @Test
    void listsKayakersAlphabeticallyByName() throws Exception {
        personService.createPerson(new PersonRequest("Зоя", null, null));
        personService.createPerson(new PersonRequest("алия", null, null));
        personService.createPerson(new PersonRequest("Алина", null, null));
        personService.createPerson(new PersonRequest("Алия", null, null));
        personService.createPerson(new PersonRequest("Борис", null, null));

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
