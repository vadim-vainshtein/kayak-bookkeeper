package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI kayakBookkeeperOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Kayak Bookkeeper API")
                .version("v1")
                .description("API для учёта тренировочных групп, посещений и оплат каяк-клуба.")
                .license(new License().name("Private API")));
    }
}
