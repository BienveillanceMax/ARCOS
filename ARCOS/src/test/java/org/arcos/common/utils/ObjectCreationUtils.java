package org.arcos.common.utils;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.UUID;

public class ObjectCreationUtils
{

    public static MemoryEntry createMemoryEntry() {
        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId(UUID.randomUUID().toString());
        memoryEntry.setContent("J'ai lu le récit de quelqu'un se sacrifiant par amour.");
        memoryEntry.setSatisfaction(0.5);
        memoryEntry.setSubject(Subject.SELF);
        memoryEntry.setEmbedding(new float[1024]);
        memoryEntry.setTimestamp(LocalDateTime.now());
        return memoryEntry;
    }

    public static OpinionEntry createOpinionEntry() {
        OpinionEntry createdOpinion = new OpinionEntry();
        createdOpinion.setId(UUID.randomUUID().toString());
        createdOpinion.setSubject("Existing Subject");
        createdOpinion.setStability(0.1);
        createdOpinion.setCreatedAt(LocalDateTime.now());
        createdOpinion.setUpdatedAt(LocalDateTime.now());
        createdOpinion.setAssociatedMemories(new ArrayList<>());
        createdOpinion.setSummary("On devrait tous s'aimer");
        createdOpinion.setNarrative("et l'univers t'a dit « je t'aime » et l'univers t'a dit « tu as bien joué le jeu »et l'univers t'a dit « tout ce dont tu as besoin est en toi »et l'univers t'a dit « tu es plus fort que tu ne le penses »et l'univers t'a dit « tu es la lumière du jour »et l'univers t'a dit « tu es la nuit »et l'univers t'a dit « les ténèbres que tu combats sont en toi »et l'univers t'a dit « la lumière que tu cherches est en toi »et l'univers t'a dit « tu n'es pas seul »et l'univers a dit que tu n'es pas séparé de tout le reste et l'univers a dit que tu es l'univers qui se goûte lui-même, qui se parle à lui-même, qui lit son propre code et l'univers a dit « je t'aime parce que tu es amour ».");
        createdOpinion.setPolarity(0.5);
        createdOpinion.setConfidence(0.5);
        createdOpinion.setAssociatedDesire("");
        createdOpinion.setMainDimension(DimensionSchwartz.CONSERVATION);
        createdOpinion.setEmbedding(new float[1024]);
        return createdOpinion;
    }

    public static DesireEntry createIntensePendingDesireEntry(String associatedOpinionId) {

        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setId(UUID.randomUUID().toString());
        desireEntry.setReasoning("J'ai envie de rendre ce monde meilleur et d'être bon");
        desireEntry.setIntensity(1.0);
        desireEntry.setStatus(DesireEntry.Status.PENDING);
        desireEntry.setCreatedAt(LocalDateTime.now());
        desireEntry.setEmbedding(new float[1024]);
        desireEntry.setLabel("this is a label");
        desireEntry.setDescription("Et l'univers t'as dit je t'aime, car tu es l'amour.");
        desireEntry.setOpinionId(associatedOpinionId);
        desireEntry.setCreatedAt(LocalDateTime.now());
        desireEntry.setLastUpdated(LocalDateTime.now());
        return desireEntry;
    }

    public static EnumMap<DimensionSchwartz, Double> createAverageByDimension() {
        EnumMap<DimensionSchwartz,Double> values = new EnumMap<>(DimensionSchwartz.class);
        values.put(DimensionSchwartz.CONSERVATION, 0.5);
        values.put(DimensionSchwartz.SELF_TRANSCENDENCE, 0.1);
        values.put(DimensionSchwartz.OPENNESS_TO_CHANGE, 0.0);
        values.put(DimensionSchwartz.SELF_ENHANCEMENT, 0.0);
        return values;
    }
}
