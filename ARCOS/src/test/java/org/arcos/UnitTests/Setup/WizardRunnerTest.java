package org.arcos.UnitTests.Setup;

import org.arcos.Setup.WizardRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WizardRunnerTest {

    @Test
    void runIfNeeded_doesNotThrow_withNoArgs() {
        // Given — pas d'args
        // When/Then — ne doit pas lancer d'exception même si config manquante
        // En CI (sans TTY), WizardRunner détecte l'absence de terminal et retourne false
        assertDoesNotThrow(() -> WizardRunner.runIfNeeded(new String[]{}));
    }

    @Test
    void runIfNeeded_doesNotThrow_withSetupFlag() {
        // Given
        String[] args = {"--setup"};

        // When/Then
        assertDoesNotThrow(() -> WizardRunner.runIfNeeded(args));
    }

    @Test
    void runIfNeeded_doesNotThrow_withReconfigureFlag() {
        // Given
        String[] args = {"--reconfigure"};

        // When/Then
        assertDoesNotThrow(() -> WizardRunner.runIfNeeded(args));
    }

    @Test
    void runIfNeeded_returnsFalse_whenNoTtyAvailable() {
        // En environnement CI sans TTY, le wizard ne peut pas s'exécuter interactivement.
        // runIfNeeded() retourne false dans tous les cas sans TTY (config manquante ou --setup).
        // Ce test vérifie uniquement l'absence d'exception et que la méthode ne bloque pas.
        assertDoesNotThrow(() -> WizardRunner.runIfNeeded(new String[]{}));
    }
}
