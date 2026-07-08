package org.arcos.UnitTests.IO.InputHandling.STT;

import org.arcos.IO.InputHandling.STT.WhisperCppAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperCppAdapterTest {

    private static byte[] wavOfDurationSec(double seconds) {
        // 16kHz mono 16-bit : 32000 octets/s + 44 de header
        return new byte[44 + (int) (seconds * 32_000)];
    }

    @Test
    void dynamicAudioCtx_ShortAudio_ShouldClampToFloor() {
        // Given / When / Then : sous ~700 whisper hallucine (matrice WER 2026-07-02) — jamais sous 750
        assertThat(WhisperCppAdapter.dynamicAudioCtx(wavOfDurationSec(2))).isEqualTo(750);
        assertThat(WhisperCppAdapter.dynamicAudioCtx(wavOfDurationSec(10))).isEqualTo(750);
        assertThat(WhisperCppAdapter.dynamicAudioCtx(wavOfDurationSec(14))).isEqualTo(750);
    }

    @Test
    void dynamicAudioCtx_LongAudio_ShouldScaleWithDurationPlusMargin() {
        // Given : 20s → 20*50 + 32 = 1032
        assertThat(WhisperCppAdapter.dynamicAudioCtx(wavOfDurationSec(20))).isEqualTo(1032);
    }

    @Test
    void dynamicAudioCtx_VeryLongAudio_ShouldClampToWhisperMax() {
        // Given : 40s dépasse la fenêtre de 30s de whisper → 1500
        assertThat(WhisperCppAdapter.dynamicAudioCtx(wavOfDurationSec(40))).isEqualTo(1500);
    }
}
