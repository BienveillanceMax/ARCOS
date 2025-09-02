package Personality;

import Exceptions.ResponseParsingException;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Desires.DesireService;
import Personality.Opinions.OpinionService;
import Personality.Values.ValueProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PersonalityOrchestrator
{
    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final DesireService desireService;
    private final ValueProfile valueProfile;

    private final int ALLOWED_RETRIES = 3;

    @Autowired
    public PersonalityOrchestrator(MemoryService memoryService, OpinionService opinionService, DesireService desireService, ValueProfile valueProfile) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
        this.valueProfile = valueProfile;
    }

    public void processMemory(String conversation) {
        MemoryEntry memoryEntry = tryMemorizing(conversation);
        if (memoryEntry == null) {
            return;
        }
        List<OpinionEntry> opinionEntries = tryFormingOpinion(memoryEntry);
        if (opinionEntries == null) {
            return;
        }
        for (OpinionEntry opinionEntry : opinionEntries) {
            tryFormingDesire(opinionEntry);
        }
    }

    private MemoryEntry tryMemorizing(String conversation) {
        MemoryEntry memoryEntry = null;
        int retries = 0;
        while (retries < ALLOWED_RETRIES) {
            try {
                memoryEntry = memoryService.memorizeConversation(conversation);
                break;
            } catch (ResponseParsingException e) {
                System.out.println(e.getMessage());
                retries++;
            }
        }
        return memoryEntry;
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

         return desireService.processOpinion(opinionEntry);
    }

}