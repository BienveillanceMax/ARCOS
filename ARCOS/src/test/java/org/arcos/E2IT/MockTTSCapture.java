package org.arcos.E2IT;

import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * TTS stub for E2E tests. Captures all speakAsync calls for assertion.
 * NOT a Spring bean — do not add @Component.
 * Injected into Orchestrator.ttsHandler via ReflectionTestUtils in BaseE2IT.
 */
public class MockTTSCapture extends PiperEmbeddedTTSModule {

    private final List<String> spokenTexts = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Future<Void> speakAsync(String text) {
        if (text != null && !text.isBlank()) spokenTexts.add(text);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Future<Void> speakAsync(String text, float lengthScale, float noiseScale, float noiseW) {
        if (text != null && !text.isBlank()) spokenTexts.add(text);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void speakAsync(String text, float lengthScale, float noiseScale, float noiseW, Runnable onDone) {
        if (text != null && !text.isBlank()) spokenTexts.add(text);
        if (onDone != null) onDone.run();
    }

    @Override
    public void afterPlayback(Runnable onDone) {
        if (onDone != null) onDone.run();
    }

    @Override
    public void shutdown() { /* no-op in tests */ }

    public List<String> getSpokenTexts() { return Collections.unmodifiableList(spokenTexts); }
    public String getAllSpokenLower() { return String.join(" ", spokenTexts).toLowerCase(); }
    public String getLastSpokenText() { return spokenTexts.isEmpty() ? null : spokenTexts.get(spokenTexts.size() - 1); }
    public boolean hasSpoken() { return !spokenTexts.isEmpty(); }
    public void clear() { spokenTexts.clear(); }
}
