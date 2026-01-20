package org.arcos.Personality;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PersonalityOrchestrator
{
    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final DesireService desireService;

    private final int ALLOWED_RETRIES = 3;

    @Autowired
    public PersonalityOrchestrator(MemoryService memoryService, OpinionService opinionService, DesireService desireService) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
    }

    public void processMemory(String conversation) {
        MemoryEntry memoryEntry = tryMemorizing(conversation);
        if (memoryEntry == null) {
            return;
        }
        processMemoryEntryIntoOpinion(memoryEntry);
    }

    private MemoryEntry tryMemorizing(String conversation) {
        MemoryEntry memoryEntry = null;
        int retries = 0;
        while (retries < ALLOWED_RETRIES) {

            memoryEntry = memoryService.memorizeConversation(conversation);
            if (memoryEntry != null) {
                break;
            }
            retries++;
        }
        return memoryEntry;
    }

    public void processMemoryEntryIntoOpinion(MemoryEntry memoryEntry) {
        List<OpinionEntry> opinionEntries = tryFormingOpinion(memoryEntry);
        if (opinionEntries == null) {
            return;
        }
        for (OpinionEntry opinionEntry : opinionEntries) {
            tryFormingDesire(opinionEntry);
        }
    }


    private List<OpinionEntry> tryFormingOpinion(MemoryEntry memoryEntry) {
        List<OpinionEntry> opinionEntry = null;
        int retries = 0;

        while (retries < ALLOWED_RETRIES) {
            opinionEntry = opinionService.processInteraction(memoryEntry);
            if (opinionEntry == null) {
                retries++;
            } else {
                break;
            }
        }
        return opinionEntry;
    }

    private DesireEntry tryFormingDesire(OpinionEntry opinionEntry) {

        if (opinionEntry == null) {
            return null;
        }
        return desireService.processOpinion(opinionEntry);
    }

}