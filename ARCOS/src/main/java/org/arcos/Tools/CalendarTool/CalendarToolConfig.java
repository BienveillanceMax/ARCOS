package org.arcos.Tools.CalendarTool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CalendarToolConfig {

    @Bean
    public CalendarService calendarService() {
        CalendarService service = new CalendarService();
        if (service.isAvailable()) {
            log.info("CalendarService initialisé (client_secrets.json trouvé).");
        } else {
            log.warn("CalendarService désactivé : client_secrets.json absent. Outil calendrier non disponible.");
        }
        return service;
    }
}
