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
    public PersonalityOrchestrator(MemoryService memoryService, OpinionService opinionService,
                                   DesireService desireService) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
    }

    public void processMemory(String conversation) {
        MemoryEntry memoryEntry = tryMemorizing(conversation);
        if (memoryEntry == null) {
            log.info("[PERSONALITY] conversation=processed memory=null opinions=0 desires=0");
            return;
        }
        int[] counts = processMemoryEntryIntoOpinion(memoryEntry);
        log.info("[PERSONALITY] conversation=processed memory=created opinions={} desires={}", counts[0], counts[1]);
    }

    private MemoryEntry tryMemorizing(String conversation) {
        MemoryEntry memoryEntry = null;
        int retries = 0;
        while (retries < ALLOWED_RETRIES) {
            try {
                memoryEntry = memoryService.memorizeConversation(conversation);
                if (memoryEntry != null) {
                    break;
                }
            } catch (Exception e) {
                log.warn("Tentative de mémorisation {} échouée : {}", retries + 1, e.getMessage());
            }
            retries++;
        }
        return memoryEntry;
    }

    public int[] processMemoryEntryIntoOpinion(MemoryEntry memoryEntry) {
        int opinionCount = 0;
        int desireCount = 0;
        List<OpinionEntry> opinionEntries = tryFormingOpinion(memoryEntry);
        if (opinionEntries == null) {
            return new int[]{opinionCount, desireCount};
        }
        opinionCount = opinionEntries.size();
        for (OpinionEntry opinionEntry : opinionEntries) {
            DesireEntry desire = tryFormingDesire(opinionEntry);
            if (desire != null) {
                desireCount++;
            }
        }
        return new int[]{opinionCount, desireCount};
    }


    private List<OpinionEntry> tryFormingOpinion(MemoryEntry memoryEntry) {
        List<OpinionEntry> opinionEntry = null;
        int retries = 0;

        while (retries < ALLOWED_RETRIES) {
            try {
                opinionEntry = opinionService.processInteraction(memoryEntry);
                if (opinionEntry == null) {
                    retries++;
                } else {
                    break;
                }
            } catch (Exception e) {
                log.warn("Tentative de formation d'opinion {} échouée : {}", retries + 1, e.getMessage());
                retries++;
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
