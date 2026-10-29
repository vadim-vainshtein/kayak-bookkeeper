package kz.kayak.bookkeeper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesDescribedOpenApiSpecification() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
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
                .andExpect(jsonPath("$.paths['/api/pricing-rates/{id}'].delete.summary")
                        .value("Удалить тариф"))
                .andExpect(jsonPath("$.paths['/api/pricing-rates/{id}'].delete.responses['404'].description")
                        .value("Тариф не найден"))
                .andExpect(jsonPath("$.paths['/api/pricing-rates'].post.responses['409'].description")
                        .value("Период пересекается с существующим тарифом того же вида"));
    }

    @Test
    void publishesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
