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
}
