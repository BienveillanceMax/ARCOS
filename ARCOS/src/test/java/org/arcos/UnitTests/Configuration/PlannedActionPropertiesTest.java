package org.arcos.UnitTests.Configuration;

import org.arcos.Configuration.PlannedActionProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlannedActionPropertiesTest {

    @Test
    void defaultValues_areCorrect() {
        PlannedActionProperties props = new PlannedActionProperties();

        assertEquals("data/planned-actions.json", props.getStoragePath());
        assertEquals("data/execution-history.json", props.getHistoryStoragePath());
        assertEquals(2, props.getThreadPoolSize());
        assertEquals(3, props.getMaxPlanRetries());
        assertEquals(5, props.getDefaultCalendarMaxResults());
    }

    @Test
    void setValues_updateCorrectly() {
        PlannedActionProperties props = new PlannedActionProperties();

        props.setStoragePath("custom/path.json");
        props.setHistoryStoragePath("custom/history.json");
        props.setThreadPoolSize(4);
        props.setMaxPlanRetries(5);
        props.setDefaultCalendarMaxResults(10);

        assertEquals("custom/path.json", props.getStoragePath());
        assertEquals("custom/history.json", props.getHistoryStoragePath());
        assertEquals(4, props.getThreadPoolSize());
        assertEquals(5, props.getMaxPlanRetries());
        assertEquals(10, props.getDefaultCalendarMaxResults());
    }
}
