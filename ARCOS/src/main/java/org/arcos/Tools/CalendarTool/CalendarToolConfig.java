package org.arcos.Tools.CalendarTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.CalDavProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CalendarToolConfig {

    @Bean
    public CalDavCalendarService calDavCalendarService(CalDavProperties calDavProperties) {
        CalDavCalendarService service = new CalDavCalendarService(calDavProperties);
        if (service.isAvailable()) {
            log.info("CalDavCalendarService initialisé (Radicale {})", calDavProperties.getUrl());
        } else {
            log.warn("CalDavCalendarService désactivé : Radicale non disponible à {}", calDavProperties.getUrl());
        }
        return service;
    }
}
