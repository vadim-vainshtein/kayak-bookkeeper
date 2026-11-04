package kz.kayak.bookkeeper.api;

import java.time.LocalDate;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.RateView;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.service.PricingRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PricingRateDeletionApiTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired private MockMvc mockMvc;
    @Autowired PricingRateService pricingRateService;

    @Test
    void deletesExistingRateWithNoContent() throws Exception {
        RateView rate = pricingRateService.createRate(new RateRequest(RateCode.POOL_RENTAL, 30_000, LocalDate.of(2026, 1, 1), null));

        mockMvc.perform(delete("/api/pricing-rates/{id}", rate.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isNoContent());

        assertThat(pricingRateService.listRates()).extracting(RateView::id).doesNotContain(rate.id());
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
