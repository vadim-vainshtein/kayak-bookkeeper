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
import java.time.LocalDateTime;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class SubscriptionCreationApiTest {
 static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46YWRtaW4=";
 @Autowired MockMvc mockMvc;
 @Autowired BookkeepingService service;
 @Test void createsExplicitSubscriptionAndReturnsSortedDatesAndMetadata() throws Exception { mockMvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION).contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":[\"2026-11-17T19:00:00\",\"2026-11-10T19:00:00\"],\"capacity\":3,\"title\":\"Ноябрь\",\"description\":\"Вечерняя группа\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.capacity").value(3)).andExpect(jsonPath("$.title").value("Ноябрь")).andExpect(jsonPath("$.description").value("Вечерняя группа")).andExpect(jsonPath("$.sessions[0].startsAt").value("2026-11-10T19:00:00")); }
 @Test void derivesRecurringDatesAndReturnsMetadata() throws Exception { mockMvc.perform(post("/api/subscriptions/recurring").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION).contentType(MediaType.APPLICATION_JSON).content("{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"19:00\",\"startDate\":\"2026-11-10\",\"sessionCount\":2,\"title\":\"Вторник\",\"description\":\"Утренняя группа\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.capacity").value(8)).andExpect(jsonPath("$.title").value("Вторник")).andExpect(jsonPath("$.description").value("Утренняя группа")).andExpect(jsonPath("$.sessions[1].startsAt").value("2026-11-17T19:00:00")); }
 @Test void acceptsAbsentOptionalMetadata() throws Exception { mockMvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION).contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":[\"2026-11-10T19:00:00\"]}")).andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(nullValue())).andExpect(jsonPath("$.description").value(nullValue())); }
 @Test void listsCompleteSubscriptionsInCreationOrder() throws Exception {
  Api.SubscriptionView first=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026,11,17,19,0)),null,"Первый","Описание первого"));
  Api.SubscriptionView second=service.createExplicitSubscription(new Api.ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026,11,10,19,0),LocalDateTime.of(2026,11,24,19,0)),3,"Второй","Описание второго"));
  mockMvc.perform(get("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].id").value(first.id()))
    .andExpect(jsonPath("$[0].title").value("Первый"))
    .andExpect(jsonPath("$[0].sessions.length()").value(1))
    .andExpect(jsonPath("$[1].id").value(second.id()))
    .andExpect(jsonPath("$[1].description").value("Описание второго"))
    .andExpect(jsonPath("$[1].sessions.length()").value(2));
 }
}
