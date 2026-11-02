package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.service.BookkeepingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinancialReportApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46YWRtaW4=";
    @Autowired private MockMvc mockMvc;
    @Autowired private BookkeepingService service;

    @Test
    void returnsDateFilteredFinancialReport() throws Exception {
        LocalDate november=LocalDate.of(2026,11,1); Api.PersonView person=service.createPerson(new Api.PersonRequest("Алия",null,null));
        service.createManualCharge(new Api.ManualChargeRequest(person.id(),5_000,november,"Ноябрьская тренировка",null));
        service.createPayment(new Api.PaymentRequest(person.id(),2_000,november.plusDays(1),PaymentMethod.CASH,null));

        mockMvc.perform(get("/api/reports/financial").param("from","2026-11-01").param("to","2026-11-30").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.summary.chargedInPeriod").value(5_000))
                .andExpect(jsonPath("$.summary.receivedInPeriod").value(2_000))
                .andExpect(jsonPath("$.summary.debtAtEnd").value(3_000))
                .andExpect(jsonPath("$.people[0].fullName").value("Алия"));
    }

    @Test
    void rejectsAnUnknownSubscriptionFilter() throws Exception {
        mockMvc.perform(get("/api/reports/financial").param("from","2026-11-01").param("to","2026-11-30").param("subscriptionId","999999").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isNotFound());
    }
}
