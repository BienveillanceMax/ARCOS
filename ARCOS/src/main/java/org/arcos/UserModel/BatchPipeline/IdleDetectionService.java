package org.arcos.UserModel.BatchPipeline;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
public class IdleDetectionService {

    private final int idleThresholdMinutes;
    private volatile LocalDateTime lastInteractionTime;

    public IdleDetectionService(UserModelProperties properties) {
        this.idleThresholdMinutes = properties.getIdleThresholdMinutes();
        this.lastInteractionTime = LocalDateTime.now();
    }

    public void recordInteraction() {
        this.lastInteractionTime = LocalDateTime.now();
        log.debug("Interaction recorded at {}", lastInteractionTime);
    }

    public boolean isIdle() {
        return getIdleDuration().toMinutes() >= idleThresholdMinutes;
    }

    public Duration getIdleDuration() {
        return Duration.between(lastInteractionTime, LocalDateTime.now());
    }
}
