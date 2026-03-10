package org.arcos.IO.InputHandling;

/**
 * Abstraction for microphone audio capture.
 * Implementations provide raw PCM data (16-bit signed LE, mono).
 */
public interface MicrophoneSource {

    /**
     * Blocking read of PCM audio data.
     *
     * @return number of bytes actually read, or -1 on error/end of stream
     */
    int read(byte[] buffer, int offset, int length);

    /** Release resources (subprocess, audio line, etc.). */
    void close();

    /** True if the source was successfully initialized and can provide audio. */
    boolean isAvailable();

    /** Human-readable description for logging. */
    String describe();
}
