package org.arcos;

import EventLoop.InputHandling.SpeechToText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${whisper.model.path}")
    private String whisperModelPath;

    @Bean
    public SpeechToText speechToText() {
        return new SpeechToText(whisperModelPath);
    }
}
