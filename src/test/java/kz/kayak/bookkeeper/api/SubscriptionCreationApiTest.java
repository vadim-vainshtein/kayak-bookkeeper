package kz.kayak.bookkeeper.api;

import java.time.LocalDateTime;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.MemberView;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonUpdateRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.RecurringSubscriptionWithMembersRequest;
import kz.kayak.bookkeeper.api.dto.SessionView;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.service.PersonService;
import kz.kayak.bookkeeper.service.PricingRateService;
import kz.kayak.bookkeeper.service.SubscriptionFinalizationService;
import kz.kayak.bookkeeper.service.SubscriptionQueryService;
import kz.kayak.bookkeeper.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubscriptionCreationApiTest {
    static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired
    MockMvc mockMvc;
    @Autowired PersonService personService;
    @Autowired PricingRateService pricingRateService;
    @Autowired SubscriptionFinalizationService subscriptionFinalizationService;
    @Autowired SubscriptionQueryService subscriptionQueryService;
    @Autowired SubscriptionService subscriptionService;

    @Test
    void createsExplicitSubscriptionAndReturnsSortedDatesAndMetadata() throws Exception {
        mockMvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .content("{\"startsAt\":[\"2026-11-17T19:00:00\",\"2026-11-10T19:00:00\"],\"capacity\":3,\"title\":\"Ноябрь\",\"description\":\"Вечерняя группа\"}"))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.capacity").value(3))
               .andExpect(jsonPath("$.title").value("Ноябрь"))
               .andExpect(jsonPath("$.description").value("Вечерняя группа"))
               .andExpect(jsonPath("$.sessions[0].startsAt").value("2026-11-10T19:00:00"));
    }

    @Test
    void derivesRecurringDatesAndReturnsMetadata() throws Exception {
        mockMvc.perform(post("/api/subscriptions/recurring").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content("{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"19:00\",\"startDate\":\"2026-11-10\",\"sessionCount\":2,\"title\":\"Вторник\",\"description\":\"Утренняя группа\"}"))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.capacity").value(8))
               .andExpect(jsonPath("$.title").value("Вторник"))
               .andExpect(jsonPath("$.description").value("Утренняя группа"))
               .andExpect(jsonPath("$.sessions[1].startsAt").value("2026-11-17T19:00:00"));
    }

    @Test
    void acceptsAbsentOptionalMetadata() throws Exception {
        mockMvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .content("{\"startsAt\":[\"2026-11-10T19:00:00\"]}"))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.title").value(nullValue()))
               .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void updatesSubscriptionMetadataWithoutChangingItsScheduleOrCapacity() throws Exception {
        SubscriptionView subscription = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026, 11, 10, 19, 0)), 3, "Старое", "Старое описание"));
        mockMvc.perform(put("/api/subscriptions/{id}/metadata", subscription.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                                                  .contentType(MediaType.APPLICATION_JSON)
                                                                                  .content("{\"title\":\"Ноябрь\",\"description\":\"Вечерняя группа\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(subscription.id()))
               .andExpect(jsonPath("$.title").value("Ноябрь"))
               .andExpect(jsonPath("$.description").value("Вечерняя группа"))
               .andExpect(jsonPath("$.capacity").value(3))
               .andExpect(jsonPath("$.sessions.length()").value(1));
        SubscriptionView updated = subscriptionQueryService.getSubscription(subscription.id());
        assertThat(updated.status()).isEqualTo(subscription.status());
    }

    @Test
    void replacesDraftScheduleAndMembersAtomically() throws Exception {
        PersonView alia = personService.createPerson(new PersonRequest("Алия", null, null));
        PersonView ivan = personService.createPerson(new PersonRequest("Иван", null, null));
        SubscriptionView subscription = subscriptionService.createRecurringSubscriptionWithMembers(new RecurringSubscriptionWithMembersRequest(java.time.DayOfWeek.TUESDAY, java.time.LocalTime.of(19, 0), java.time.LocalDate.of(2026, 11, 10), 2, 2, "Старый", "Старое", List.of(new MemberRequest(alia.id(), false, false, false))));
        String body = String.format("{\"startsAt\":[\"2026-11-24T20:00:00\",\"2026-11-17T18:30:00\"],\"members\":[{\"personId\":%d,\"usesSkirt\":true,\"usesPaddle\":false,\"usesKayak\":true},{\"personId\":%d,\"usesSkirt\":false,\"usesPaddle\":true,\"usesKayak\":false}],\"capacity\":3,\"title\":\"Новый\",\"description\":\"Новое описание\"}", ivan.id(), alia.id());
        mockMvc.perform(
                       put("/api/subscriptions/{id}/draft", subscription.id())
                               .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                               .contentType(MediaType.APPLICATION_JSON)
                               .content(body))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.title").value("Новый"))
               .andExpect(jsonPath("$.capacity").value(3))
               .andExpect(jsonPath("$.sessions.length()").value(2))
               .andExpect(jsonPath("$.sessions[0].startsAt").value("2026-11-17T18:30:00"))
               .andExpect(jsonPath("$.members.length()").value(2))
               .andExpect(jsonPath("$.members[0].personName").value("Алия"))
               .andExpect(jsonPath("$.members[0].usesSkirt").value(false))
               .andExpect(jsonPath("$.members[1].usesPaddle").value(false));
        SubscriptionView updated = subscriptionQueryService.getSubscription(subscription.id());
        assertThat(updated.status()).isEqualTo(kz.kayak.bookkeeper.domain.SubscriptionStatus.DRAFT);
        assertThat(updated.sessions()).extracting(SessionView::startsAt)
                                      .containsExactly(LocalDateTime.of(2026, 11, 17, 18, 30), LocalDateTime.of(2026, 11, 24, 20, 0));
    }

    @Test
    void rejectsDraftReplacementAfterSubscriptionIsFinalized() throws Exception {
        PersonView person = personService.createPerson(new PersonRequest("Алия", null, null));
        pricingRateService.createRate(new RateRequest(kz.kayak.bookkeeper.domain.RateCode.POOL_RENTAL, 100, java.time.LocalDate.of(2026, 11, 1), null));
        SubscriptionView subscription = subscriptionService.createRecurringSubscriptionWithMembers(new RecurringSubscriptionWithMembersRequest(java.time.DayOfWeek.TUESDAY, java.time.LocalTime.of(19, 0), java.time.LocalDate.of(2026, 11, 10), 1, 1, "Ноябрь", null, List.of(new MemberRequest(person.id(), false, false, false))));
        subscriptionFinalizationService.finalizeSubscription(subscription.id());
        mockMvc.perform(put("/api/subscriptions/{id}/draft", subscription.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(String.format("{\"startsAt\":[\"2026-11-17T19:00:00\"],\"members\":[{\"personId\":%d}],\"title\":\"Ноябрь\"}", person.id())))
               .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidDraftScheduleAndMembersBeforeReplacingExistingData() throws Exception {
        PersonView alia = personService.createPerson(new PersonRequest("Алия", null, null));
        PersonView ivan = personService.createPerson(new PersonRequest("Иван", null, null));
        PersonView inactive = personService.createPerson(new PersonRequest("Юлия", null, null));
        personService.updatePerson(inactive.id(), new PersonUpdateRequest("Юлия", null, null, false));
        SubscriptionView subscription = subscriptionService.createRecurringSubscriptionWithMembers(new RecurringSubscriptionWithMembersRequest(java.time.DayOfWeek.TUESDAY, java.time.LocalTime.of(19, 0), java.time.LocalDate.of(2026, 11, 10), 1, 1, "Ноябрь", null, List.of(new MemberRequest(alia.id(), false, false, false))));
        mockMvc.perform(put("/api/subscriptions/{id}/draft", subscription.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(String.format("{\"startsAt\":[\"2026-11-17T19:00:00\",\"2026-11-17T19:00:00\"],\"members\":[{\"personId\":%d}]}", alia.id())))
               .andExpect(status().isConflict());
        mockMvc.perform(put("/api/subscriptions/{id}/draft", subscription.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(String.format("{\"startsAt\":[\"2026-11-17T19:00:00\"],\"members\":[{\"personId\":%d},{\"personId\":%d}]}", alia.id(), alia.id())))
               .andExpect(status().isConflict());
        mockMvc.perform(put("/api/subscriptions/{id}/draft", subscription.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(String.format("{\"startsAt\":[\"2026-11-17T19:00:00\"],\"members\":[{\"personId\":%d},{\"personId\":%d}]}", alia.id(), ivan.id())))
               .andExpect(status().isConflict());
        mockMvc.perform(put("/api/subscriptions/{id}/draft", subscription.id()).header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(String.format("{\"startsAt\":[\"2026-11-17T19:00:00\"],\"members\":[{\"personId\":%d}]}", inactive.id())))
               .andExpect(status().isConflict());
        SubscriptionView unchanged = subscriptionQueryService.getSubscription(subscription.id());
        assertThat(unchanged.sessions()).extracting(SessionView::startsAt)
                                        .containsExactly(LocalDateTime.of(2026, 11, 10, 19, 0));
        assertThat(unchanged.members()).extracting(MemberView::personId)
                                       .containsExactly(alia.id());
    }

    @Test
    void listsCompleteSubscriptionsInCreationOrder() throws Exception {
        SubscriptionView first = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026, 11, 17, 19, 0)), null, "Первый", "Описание первого"));
        SubscriptionView second = subscriptionService.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(LocalDateTime.of(2026, 11, 10, 19, 0), LocalDateTime.of(2026, 11, 24, 19, 0)), 3, "Второй", "Описание второго"));
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
