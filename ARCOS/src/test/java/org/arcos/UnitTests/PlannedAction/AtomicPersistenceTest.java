package org.arcos.UnitTests.PlannedAction;

import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionRepository;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Repository write leaves a complete file and no .tmp residue")
    void repository_persist_isAtomicAndComplete() throws IOException {
        Path target = tempDir.resolve("planned-actions.json");
        PlannedActionProperties props = new PlannedActionProperties();
        props.setStoragePath(target.toString());
        PlannedActionRepository repo = new PlannedActionRepository(props);

        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        repo.save(entry);

        assertThat(Files.exists(target)).isTrue();
        String content = Files.readString(target);
        assertThat(content).contains(entry.getId());
        assertThat(content.trim()).endsWith("}");
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".tmp"));
        }
    }

    @Test
    @DisplayName("History write leaves a complete file and no .tmp residue")
    void history_persist_isAtomicAndComplete() throws IOException {
        Path target = tempDir.resolve("execution-history.json");
        PlannedActionProperties props = new PlannedActionProperties();
        props.setHistoryStoragePath(target.toString());
        ExecutionHistoryService service = new ExecutionHistoryService(props);

        PlannedActionEntry action = ObjectCreationUtils.createSimpleReminderEntry();
        service.recordExecution(action, "ok", true);

        String content = Files.readString(target);
        assertThat(content).contains(action.getId());
        assertThat(content.trim()).endsWith("]");
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".tmp"));
        }
    }
}
