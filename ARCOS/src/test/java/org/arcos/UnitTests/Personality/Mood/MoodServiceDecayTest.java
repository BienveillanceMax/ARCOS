package org.arcos.UnitTests.Personality.Mood;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Mood.PadState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour la décroissance PAD homestatique (T4-001/T4-002).
 */
@ExtendWith(MockitoExtension.class)
class MoodServiceDecayTest {

    @Mock
    private MoodStateHolder moodStateHolder;

    private PersonalityProperties personalityProperties;
    private MoodService moodService;

    @BeforeEach
    void setUp() {
        personalityProperties = new PersonalityProperties();
        personalityProperties.setProfile("CALCIFER");
        moodService = new MoodService(moodStateHolder, personalityProperties);
    }

    // ── T4-001 : Décroissance d'un cycle ──────────────────────────────────────

    @Test
    void applyDecay_SingleCycle_ShouldConvergeTowardBaseline() {
        // Given: PAD extrême, profil CALCIFER (baseline P=0.3, A=0.2, D=0.1), f=0.95
        PadState extreme = new PadState(-0.8, 0.8, 0.8);
        when(moodStateHolder.getPadState()).thenReturn(extreme);

        // When
        moodService.applyDecay();

        // Then: new_P = -0.8 * 0.95 + 0.3 * 0.05 = -0.760 + 0.015 = -0.745
        ArgumentCaptor<PadState> captor = ArgumentCaptor.forClass(PadState.class);
        verify(moodStateHolder).setPadState(captor.capture());
        PadState result = captor.getValue();
        assertEquals(-0.745, result.getPleasure(), 0.001, "Plaisir décroît vers baseline");
        // new_A = 0.8 * 0.95 + 0.2 * 0.05 = 0.760 + 0.010 = 0.770
        assertEquals(0.770, result.getArousal(), 0.001, "Activation décroît vers baseline");
        // new_D = 0.8 * 0.95 + 0.1 * 0.05 = 0.760 + 0.005 = 0.765
        assertEquals(0.765, result.getDominance(), 0.001, "Dominance décroît vers baseline");
    }

    @Test
    void applyDecay_NeutralState_ShouldConvergeTowardCalciferBaseline() {
        // Given: PAD neutre (0,0,0), profil CALCIFER (baseline P=0.3, A=0.2, D=0.1)
        PadState neutral = new PadState(0.0, 0.0, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(neutral);

        // When
        moodService.applyDecay();

        // Then: new_P = 0.0 * 0.95 + 0.3 * 0.05 = 0.015
        ArgumentCaptor<PadState> captor = ArgumentCaptor.forClass(PadState.class);
        verify(moodStateHolder).setPadState(captor.capture());
        PadState result = captor.getValue();
        assertEquals(0.015, result.getPleasure(), 0.001, "PAD converge positivement depuis 0");
    }

    // ── T4-002 : Baselines par profil ─────────────────────────────────────────

    @Test
    void getMoodBaselineForProfile_Calcifer_ShouldReturnCorrectBaseline() {
        personalityProperties.setProfile("CALCIFER");
        PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();
        assertEquals(0.3, baseline.getPleasure(), 0.001);
        assertEquals(0.2, baseline.getArousal(), 0.001);
        assertEquals(0.1, baseline.getDominance(), 0.001);
    }

    @Test
    void getMoodBaselineForProfile_K2SO_ShouldReturnCorrectBaseline() {
        personalityProperties.setProfile("K2SO");
        PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();
        assertEquals(-0.1, baseline.getPleasure(), 0.001);
        assertEquals(0.1, baseline.getArousal(), 0.001);
        assertEquals(0.0, baseline.getDominance(), 0.001);
    }

    @Test
    void getMoodBaselineForProfile_Glados_ShouldReturnCorrectBaseline() {
        personalityProperties.setProfile("GLADOS");
        PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();
        assertEquals(-0.2, baseline.getPleasure(), 0.001);
        assertEquals(0.0, baseline.getArousal(), 0.001);
        assertEquals(0.3, baseline.getDominance(), 0.001);
    }

    @Test
    void getMoodBaselineForProfile_Default_ShouldReturnZeroBaseline() {
        personalityProperties.setProfile("DEFAULT");
        PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();
        assertEquals(0.0, baseline.getPleasure(), 0.001);
        assertEquals(0.0, baseline.getArousal(), 0.001);
        assertEquals(0.0, baseline.getDominance(), 0.001);
    }

    @Test
    void getMoodBaselineForProfile_UnknownProfile_ShouldFallbackToZero() {
        personalityProperties.setProfile("UNKNOWN_PROFILE");
        PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();
        assertEquals(0.0, baseline.getPleasure(), 0.001, "Profil inconnu → baseline (0,0,0)");
    }

    // ── T4-003 : Seuil d'initiative ajusté par l'humeur ───────────────────────

    @Test
    void getEffectiveInitiativeThreshold_PositivePleasure_ShouldLowerThreshold() {
        // Given: plaisir=0.5, base=0.80, factor=0.15
        // Expected: 0.80 * (1 - 0.5 * 0.15) = 0.80 * 0.925 = 0.74
        PadState happyMood = new PadState(0.5, 0.0, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(happyMood);

        double adjusted = moodService.getEffectiveInitiativeThreshold(0.80);
        assertEquals(0.74, adjusted, 0.01, "Plaisir positif abaisse le seuil d'initiative");
    }

    @Test
    void getEffectiveInitiativeThreshold_NegativePleasure_ShouldRaiseThreshold() {
        // Given: plaisir=-0.6, base=0.80, factor=0.15
        // Expected: 0.80 * (1 - (-0.6) * 0.15) = 0.80 * 1.09 = 0.872
        PadState sadMood = new PadState(-0.6, 0.0, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(sadMood);

        double adjusted = moodService.getEffectiveInitiativeThreshold(0.80);
        assertEquals(0.872, adjusted, 0.01, "Plaisir négatif élève le seuil d'initiative");
    }

    @Test
    void getEffectiveInitiativeThreshold_NeutralPleasure_ShouldReturnBaseThreshold() {
        // Given: plaisir=0.0, factor=0.15 → aucun ajustement
        PadState neutralMood = new PadState(0.0, 0.0, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(neutralMood);

        double adjusted = moodService.getEffectiveInitiativeThreshold(0.80);
        assertEquals(0.80, adjusted, 0.001, "Plaisir neutre = seuil inchangé");
    }

    // ── T4-004 : Volatilité des opinions par l'Arousal ────────────────────────

    @Test
    void getMoodOpinionVolatilityFactor_HighArousal_ShouldIncreaseVolatility() {
        // Given: arousal=0.8, weight=0.5 → factor = 1.0 + 0.8 * 0.5 = 1.4
        PadState highArousal = new PadState(0.0, 0.8, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(highArousal);

        double factor = moodService.getMoodOpinionVolatilityFactor();
        assertEquals(1.4, factor, 0.001, "Arousal 0.8 → facteur 1.4x");
    }

    @Test
    void getMoodOpinionVolatilityFactor_NeutralArousal_ShouldReturnOne() {
        // Given: arousal=0.0 → factor = 1.0
        PadState neutralMood = new PadState(0.0, 0.0, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(neutralMood);

        double factor = moodService.getMoodOpinionVolatilityFactor();
        assertEquals(1.0, factor, 0.001, "Arousal 0 → facteur neutre 1.0x");
    }

    @Test
    void getMoodOpinionVolatilityFactor_NegativeArousal_ShouldReturnOne() {
        // Given: arousal=-0.5 → Math.max(0, -0.5) = 0 → factor = 1.0
        PadState lowArousal = new PadState(0.0, -0.5, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(lowArousal);

        double factor = moodService.getMoodOpinionVolatilityFactor();
        assertEquals(1.0, factor, 0.001, "Arousal négatif → facteur plafonné à 1.0x");
    }

    // ── Gaps comblés — Sprint 8, Story 1.12 ─────────────────────────────────

    @Test
    void applyMoodUpdate_WithNull_ShouldNotThrow() {
        // Given/When/Then — null update is silently ignored
        assertDoesNotThrow(() -> moodService.applyMoodUpdate(null));
    }

    @Test
    void applyMoodUpdate_WithLargeDeltas_ShouldClamp() {
        // Given: PAD at (0.8, -0.8, 0.5), deltas that push past bounds
        PadState initial = new PadState(0.8, -0.8, 0.5);
        when(moodStateHolder.getPadState()).thenReturn(initial);

        MoodUpdate update = new MoodUpdate();
        update.deltaPleasure = 0.5;   // 0.8 + 0.5 = 1.3 → clamp to 1.0
        update.deltaArousal = -0.5;   // -0.8 + (-0.5) = -1.3 → clamp to -1.0
        update.deltaDominance = 0.0;
        update.reasoning = "test clamping";

        // When
        moodService.applyMoodUpdate(update);

        // Then
        ArgumentCaptor<PadState> captor = ArgumentCaptor.forClass(PadState.class);
        verify(moodStateHolder).setPadState(captor.capture());
        PadState result = captor.getValue();
        assertEquals(1.0, result.getPleasure(), 0.001, "Pleasure should clamp at 1.0");
        assertEquals(-1.0, result.getArousal(), 0.001, "Arousal should clamp at -1.0");
        assertEquals(0.5, result.getDominance(), 0.001, "Dominance unchanged");
    }

    @Test
    void applyDecay_MultipleCycles_ShouldConvergeTowardBaseline() {
        // Given: PAD extreme, CALCIFER baseline (0.3, 0.2, 0.1), f=0.95
        PadState current = new PadState(-0.8, 0.8, 0.8);
        PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();

        // When: run 50 real decay cycles through the service
        for (int i = 0; i < 50; i++) {
            when(moodStateHolder.getPadState()).thenReturn(current);
            moodService.applyDecay();
            ArgumentCaptor<PadState> captor = ArgumentCaptor.forClass(PadState.class);
            verify(moodStateHolder, atLeast(i + 1)).setPadState(captor.capture());
            current = captor.getValue();
        }

        // Then: should be very close to baseline after 50 cycles
        assertEquals(baseline.getPleasure(), current.getPleasure(), 0.1, "Pleasure converges to baseline");
        assertEquals(baseline.getArousal(), current.getArousal(), 0.1, "Arousal converges to baseline");
        assertEquals(baseline.getDominance(), current.getDominance(), 0.1, "Dominance converges to baseline");
    }
}
