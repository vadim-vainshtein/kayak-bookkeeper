package kz.kayak.bookkeeper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    private static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46dGVzdC1hZG1pbi1wYXNzd29yZA==";
    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesDescribedOpenApiSpecification() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/attendance'].put.summary").value("Сохранить посещения тренировки"))
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/drop-ins']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/roster']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AttendanceView").exists())
                .andExpect(jsonPath("$.paths['/api/reports/attendance'].get.summary").value("Получить отчёт по посещениям абонементов"))
                .andExpect(jsonPath("$.components.schemas.AttendanceReportView.properties.timeZone.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ChargeView.properties.attendanceId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SubscriptionFinancialReportView.properties.cashBalance.type").value("integer"))
                .andExpect(jsonPath("$.info.title").value("Kayak Bookkeeper API"))
                .andExpect(jsonPath("$.paths['/api/people'].post.summary").value("Добавить каякера"))
                .andExpect(jsonPath("$.paths['/api/people/{id}'].put.summary").value("Изменить карточку каякера"))
                .andExpect(jsonPath("$.paths['/api/people/{id}'].put.responses['404'].description").value("Каякер не найден"))
                .andExpect(jsonPath("$.paths['/api/subscriptions/{id}/finalize'].post.summary")
                        .value("Финализировать абонемент"))
                .andExpect(jsonPath("$.paths['/api/subscriptions'].post.summary")
                        .value("Создать абонемент по списку тренировок"))
                .andExpect(jsonPath("$.paths['/api/subscriptions'].get.summary")
                        .value("Получить все абонементы"))
                .andExpect(jsonPath("$.paths['/api/subscriptions/recurring'].post.summary")
                        .value("Создать абонемент по еженедельному расписанию"))
                .andExpect(jsonPath("$.paths['/api/charges'].post.summary")
                        .value("Создать ручное начисление"))
                .andExpect(jsonPath("$.paths['/api/payments/{id}'].patch.summary")
                        .value("Изменить сумму, дату и комментарий платежа"))
                .andExpect(jsonPath("$.paths['/api/payments/{id}'].patch.responses['409'].description")
                        .value("Сумма меньше уже распределённой суммы"))
                .andExpect(jsonPath("$.paths['/api/payments/{paymentId}/allocations/{allocationId}'].delete.summary")
                        .value("Отменить распределение платежа"))
                .andExpect(jsonPath("$.paths['/api/pricing-rates/{id}'].delete.summary")
                        .value("Удалить тариф"))
                .andExpect(jsonPath("$.paths['/api/pricing-rates/{id}'].delete.responses['404'].description")
                        .value("Тариф не найден"))
                .andExpect(jsonPath("$.paths['/api/pricing-rates'].post.responses['409'].description")
                        .value("Период пересекается с существующим тарифом того же вида"));
    }

    @Test
    void preservesDtoSchemaNamesAndNestedValidationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/people'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PersonRequest"))
                .andExpect(jsonPath("$.components.schemas.PersonRequest.required").value(hasItem("fullName")))
                .andExpect(jsonPath("$.components.schemas.PaymentAmountRequest.properties.paidAt.format").value("date"))
                .andExpect(jsonPath("$.components.schemas.PaymentAmountRequest.properties.note.maxLength").value(1000))
                .andExpect(jsonPath("$.components.schemas.ExplicitSubscriptionRequest.properties.startsAt.items.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.RecurringSubscriptionWithMembersRequest.properties.members.minItems")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.RecurringSubscriptionWithMembersRequest.properties.members.items['$ref']")
                        .value("#/components/schemas/MemberRequest"))
                .andExpect(jsonPath("$.components.schemas.SubscriptionView.properties.members.items['$ref']")
                        .value("#/components/schemas/MemberView"));
    }

    @Test
    void publishesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui/index.html").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk());
    }
}
