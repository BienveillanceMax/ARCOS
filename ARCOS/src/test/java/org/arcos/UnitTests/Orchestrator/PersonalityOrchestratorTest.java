package org.arcos.UnitTests.Orchestrator;

import org.arcos.Exceptions.ResponseParsingException;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.PersonalityOrchestrator;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalityOrchestratorTest {

    @InjectMocks
    private PersonalityOrchestrator personalityOrchestrator;

    @Mock
    private MemoryService memoryService;

    @Mock
    private OpinionService opinionService;

    @Mock
    private DesireService desireService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Logger logger = (Logger) LoggerFactory.getLogger(PersonalityOrchestrator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @Test
    void processMemory_SuccessPath_ShouldCallAllServices() throws Exception {
        // Given
        String conversation = "test conversation";
        MemoryEntry memoryEntry = new MemoryEntry();
        OpinionEntry opinionEntry = new OpinionEntry();
        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.singletonList(opinionEntry));
        when(desireService.processOpinion(opinionEntry)).thenReturn(new DesireEntry());

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        verify(memoryService).memorizeConversation(conversation);
        verify(opinionService).processInteraction(memoryEntry);
        verify(desireService).processOpinion(opinionEntry);
    }

    @Test
    void processMemory_MemorizationFails_ShouldNotProceed() throws Exception {
        // Given
        String conversation = "test conversation";
        doAnswer(inv -> { throw new ResponseParsingException("test exception"); })
            .when(memoryService).memorizeConversation(conversation);

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        verify(memoryService, times(3)).memorizeConversation(conversation);
        verify(opinionService, never()).processInteraction(any(MemoryEntry.class));
        verify(desireService, never()).processOpinion(any(OpinionEntry.class));
    }

    @Test
    void processMemory_OpinionFormationFails_ShouldNotCreateDesire() throws Exception {
        // Given
        String conversation = "test conversation";
        MemoryEntry memoryEntry = new MemoryEntry();
        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(null);

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        verify(memoryService).memorizeConversation(conversation);
        verify(opinionService, times(3)).processInteraction(memoryEntry);
        verify(desireService, never()).processOpinion(any(OpinionEntry.class));
    }

    @Test
    void processMemory_SuccessPath_ShouldEmitStructuredLog() throws Exception {
        // Given
        String conversation = "test conversation";
        MemoryEntry memoryEntry = new MemoryEntry();
        OpinionEntry opinionEntry = new OpinionEntry();
        when(memoryService.memorizeConversation(conversation)).thenReturn(memoryEntry);
        when(opinionService.processInteraction(memoryEntry)).thenReturn(Collections.singletonList(opinionEntry));
        when(desireService.processOpinion(opinionEntry)).thenReturn(new DesireEntry());

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("[PERSONALITY] conversation=processed memory=created opinions=1 desires=1"));
    }

    @Test
    void processMemory_MemorizationReturnsNull_ShouldLogFiltered() throws Exception {
        // Given
        String conversation = "test conversation";
        when(memoryService.memorizeConversation(conversation)).thenReturn(null);

        // When
        personalityOrchestrator.processMemory(conversation);

        // Then
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("[PERSONALITY] conversation=processed memory=null opinions=0 desires=0"));
    }
}

