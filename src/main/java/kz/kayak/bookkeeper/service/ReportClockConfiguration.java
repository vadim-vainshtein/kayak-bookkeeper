package kz.kayak.bookkeeper.service;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportClockConfiguration {
    @Bean
    Clock reportClock(@Value("${app.time-zone:Asia/Qyzylorda}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
