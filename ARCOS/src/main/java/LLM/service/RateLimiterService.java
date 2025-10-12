package LLM.service;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private final RateLimiter rateLimiter = RateLimiter.create(0.9);

    public void acquirePermit() {
        rateLimiter.acquire();
    }
}