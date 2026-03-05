package org.arcos.Personality;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.UserModel.Extraction.UserTreeUpdater;
import org.arcos.UserModel.Models.ObservationCandidate;
import org.arcos.UserModel.Models.ObservationCandidateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PersonalityOrchestrator
{
    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final DesireService desireService;
    private final UserTreeUpdater userTreeUpdater;

    private final int ALLOWED_RETRIES = 3;

    @Autowired
    public PersonalityOrchestrator(MemoryService memoryService, OpinionService opinionService,
                                   DesireService desireService, @Nullable UserTreeUpdater userTreeUpdater) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
        this.userTreeUpdater = userTreeUpdater;
    }

    public void processMemory(String conversation) {
        MemoryEntry memoryEntry = tryMemorizing(conversation);
        if (memoryEntry == null) {
            return;
        }
        routeObservationsIfPresent();
        processMemoryEntryIntoOpinion(memoryEntry);
    }

    private void routeObservationsIfPresent() {
        if (userTreeUpdater == null) {
            return;
        }
        List<ObservationCandidateDto> observations = memoryService.getAndClearLastObservations();
        if (observations == null || observations.isEmpty()) {
            return;
        }
        for (ObservationCandidateDto dto : observations) {
            try {
                ObservationCandidate candidate = ObservationCandidate.fromDto(dto);
                UserTreeUpdater.UpdateResult result = userTreeUpdater.processObservation(candidate);
                log.debug("Observation routed: {} -> {}", candidate.text(), result);
            } catch (Exception e) {
                log.warn("Failed to route observation: {}", e.getMessage());
            }
        }
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