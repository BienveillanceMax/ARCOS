package org.arcos.UnitTests.Setup;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.WizardContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WizardContextTest {

    @Test
    void defaultConstructor_createsEmptyModel() {
        // When
        WizardContext context = new WizardContext();

        // Then
        assertNotNull(context.getModel());
        assertFalse(context.isUserConfirmedSave());
        assertTrue(context.getWarnings().isEmpty());
        assertTrue(context.getServiceCheckResults().isEmpty());
    }

    @Test
    void constructor_withExistingModel_usesIt() {
        // Given
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test");

        // When
        WizardContext context = new WizardContext(model);

        // Then
        assertEquals("sk-test", context.getModel().getMistralApiKey());
    }

    @Test
    void addWarning_storesWarning() {
        // Given
        WizardContext context = new WizardContext();

        // When
        context.addWarning("test warning");

        // Then
        assertEquals(1, context.getWarnings().size());
        assertEquals("test warning", context.getWarnings().get(0));
    }

    @Test
    void getWarnings_returnsImmutableList() {
        // Given
        WizardContext context = new WizardContext();
        context.addWarning("warning");

        // When / Then
        assertThrows(UnsupportedOperationException.class,
                () -> context.getWarnings().add("new warning"));
    }

    @Test
    void addServiceCheckResult_storesResult() {
        // Given
        WizardContext context = new WizardContext();

        // When
        context.addServiceCheckResult("Qdrant", "ONLINE");

        // Then
        assertEquals("ONLINE", context.getServiceCheckResults().get("Qdrant"));
    }

    @Test
    void setUserConfirmedSave_updatesFlag() {
        // Given
        WizardContext context = new WizardContext();

        // When
        context.setUserConfirmedSave(true);

        // Then
        assertTrue(context.isUserConfirmedSave());
    }
}
