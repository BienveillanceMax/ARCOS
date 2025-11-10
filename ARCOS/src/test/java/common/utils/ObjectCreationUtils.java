package common.utils;

import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.Subject;
import Personality.Opinions.OpinionService;
import Personality.Values.Entities.DimensionSchwartz;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

public class ObjectCreationUtils
{

    public static MemoryEntry createMemoryEntry() {
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId(UUID.randomUUID().toString());
        memoryEntry.setContent("content");
        memoryEntry.setSatisfaction(0.5);
        memoryEntry.setSubject(Subject.SELF);
        memoryEntry.setEmbedding(new float[1024]);
        memoryEntry.setTimestamp(LocalDateTime.now());
        return memoryEntry;
    }

    public static OpinionEntry createOpinionEntry() {
        OpinionEntry createdOpinion = new OpinionEntry();
        createdOpinion.setId("opinion-1");
        createdOpinion.setSubject("Existing Subject");
        createdOpinion.setStability(0.1);
        createdOpinion.setCreatedAt(LocalDateTime.now());
        createdOpinion.setUpdatedAt(LocalDateTime.now());
        createdOpinion.setAssociatedMemories(new ArrayList<>());
        createdOpinion.setSummary("summary");
        createdOpinion.setNarrative("narrative");
        createdOpinion.setPolarity(0.5);
        createdOpinion.setConfidence(0.5);
        createdOpinion.setAssociatedDesire("desire-1");
        createdOpinion.setMainDimension(DimensionSchwartz.CONSERVATION);
        return createdOpinion;
    }

    public static DesireEntry createIntensePendingDesireEntry(String associatedOpinionId) {
        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setId(UUID.randomUUID().toString());
        desireEntry.setReasoning("I've reasoned far and wide and this is the only conclusion : we must love each other without limits or reasons");
        desireEntry.setIntensity(1.0);
        desireEntry.setStatus(DesireEntry.Status.PENDING);
        desireEntry.setCreatedAt(LocalDateTime.now());
        desireEntry.setEmbedding(new float[1024]);
        desireEntry.setLabel("this is a label");
        desireEntry.setDescription("this is a description");
        desireEntry.setOpinionId(associatedOpinionId);
        desireEntry.setCreatedAt(LocalDateTime.now());
        desireEntry.setLastUpdated(LocalDateTime.now());
        return desireEntry;
    }
}
