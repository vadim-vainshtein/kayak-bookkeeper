package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.service.BookkeepingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PricingRateDeletionApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46YWRtaW4=";
    @Autowired private MockMvc mockMvc;
    @Autowired private BookkeepingService service;

    @Test
    void deletesExistingRateWithNoContent() throws Exception {
        Api.RateView rate = service.createRate(new Api.RateRequest(RateCode.POOL_RENTAL, 30_000, LocalDate.of(2026, 1, 1), null));

        mockMvc.perform(delete("/api/pricing-rates/{id}", rate.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isNoContent());

        assertThat(service.listRates()).extracting(Api.RateView::id).doesNotContain(rate.id());
    }

    @Test
    void returnsNotFoundForUnknownRate() throws Exception {
        mockMvc.perform(delete("/api/pricing-rates/{id}", 999_999L).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pricing rate 999999 was not found"));
    }

    @Test
    void createsZeroAmountRate() throws Exception {
        mockMvc.perform(post("/api/pricing-rates")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"POOL_RENTAL\",\"amount\":0,\"effectiveFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(0));
    }
}
