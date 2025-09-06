package Tools.CalendarTool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarToolConfig {

    @Bean
    public CalendarService calendarService() {
        return new CalendarService();
    }
}
