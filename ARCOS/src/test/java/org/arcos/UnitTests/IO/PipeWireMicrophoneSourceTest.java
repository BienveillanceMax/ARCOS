package org.arcos.UnitTests.IO;

import org.arcos.IO.InputHandling.PipeWireMicrophoneSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PipeWireMicrophoneSourceTest {

    /** Always-EOF stream: simulates a dead pw-record pipe. */
    private static final class EofInputStream extends InputStream {
        @Override public int read() { return -1; }
    }

    /** Always-readable stream: simulates a healthy pw-record pipe. */
    private static final class ConstInputStream extends InputStream {
        private final int value;
        ConstInputStream(int value) { this.value = value; }
        @Override public int read() { return value; }
    }

    /** Fake alive Process exposing the given stdout stream. */
    private static Process fakeProcess(InputStream stdout) {
        return new Process() {
            @Override public InputStream getInputStream() { return stdout; }
            @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            @Override public int waitFor() { return 0; }
            @Override public int exitValue() { throw new IllegalThreadStateException("alive"); }
            @Override public void destroy() { }
            @Override public Process destroyForcibly() { return this; }
            @Override public boolean isAlive() { return true; }
        };
    }

    @Test
    void read_doesOneInStreamReinit_andReturnsRecoveredBytes() {
        AtomicInteger launches = new AtomicInteger();
        PipeWireMicrophoneSource src = new PipeWireMicrophoneSource() {
            @Override protected Process launch() {
                launches.incrementAndGet();
                return fakeProcess(launches.get() == 1
                        ? new EofInputStream()       // dies: read()==-1
                        : new ConstInputStream(7));  // healthy after reinit
            }
        };
        byte[] buf = new byte[16];
        int n = src.read(buf, 0, buf.length);
        assertThat(launches.get()).isEqualTo(2);   // exactly one reinit, not a spin
        assertThat(n).isGreaterThan(0);
    }

    @Test
    void read_returnsMinusOnePromptly_whenReinitFails() {
        PipeWireMicrophoneSource src = new PipeWireMicrophoneSource() {
            @Override protected Process launch() {
                return fakeProcess(new EofInputStream()); // every launch is dead
            }
        };
        byte[] buf = new byte[16];
        int n = src.read(buf, 0, buf.length);
        assertThat(n).isEqualTo(-1);   // gives up THIS call (loop owns the retry), no infinite loop
    }
}
