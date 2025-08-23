package Personality.Opinions;

import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.SearchResult;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.Entities.ValueSchwartz;
import Personality.Values.ValueProfile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
public class OpinionService
{

    private final ValueProfile valueProfile;
    int BATCH_SIZE = 10;
    private final PromptBuilder promptBuilder;
    private final MemoryService memoryService;
    private final LLMClient llmClient;
    private final LLMResponseParser llmResponseParser;

    // ---- Hyperparamètres (ajuste les valeurs si besoin) ----
    private static final double W_EXPERIENCE = 0.70;   // poids experience dans polarity
    private static final double W_COHERENCE = 0.30;    // poids coherence valeur -> polarity

    private static final double BASE_CONF_WEIGHT = 0.5; // pour init confiance
    private static final double IMP_CONF_WEIGHT = 0.5;  // pour init confiance

    private static final double BETA_IMP = 0.60;   // pour stabilité: importance
    private static final double BETA_ALIGN = 0.40; // pour stabilité: alignment

    private static final double ALPHA_POL_UPDATE = 0.0; // non utilisé (inertie via oldS used)
    private static final double REINFORCE_BASE = 0.05; // gain confiance quand cohérent
    private static final double CONTRADICT_BASE = 0.08; // perte confiance si contradictoire

    private static final double STABILITY_GROWTH = 0.02; // croissance stabilité si renforcé
    private static final double STABILITY_SHRINK = 0.03; // baisse stabilité si contradictoire

    // extended params
    private static final double RHO_NETWORK = 0.25; // poids du réseau dans expEffective


    public OpinionService(LLMResponseParser llmResponseParser, LLMClient llmClient, MemoryService memoryService, PromptBuilder promptBuilder, ValueProfile valueProfile) {
        this.llmResponseParser = llmResponseParser;
        this.llmClient = llmClient;
        this.memoryService = memoryService;
        this.promptBuilder = promptBuilder;
        this.valueProfile = valueProfile;
    }


    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static int sign(double x) {
        if (x > 0) return 1;
        if (x < 0) return -1;
        return 0;
    }

    private static double normalize0to1(double v100) {
        // transform 0..100 -> 0..1
        return clamp(v100 / 100.0, 0.0, 1.0);
    }


/// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Prend en paramètre un souvenir et crée une opinion incomplète (ie avec uniquement les champs verbeux)
     *
     * @param memoryEntry Le souvenir source
     * @return Une OpinionEntry avec les champs verbeux remplis
     */
    private OpinionEntry getOpinionFromMemoryEntry(MemoryEntry memoryEntry) {
        //gotta make a call, build a new prompt and parse the return

        OpinionEntry opinionEntry;
        String prompt = promptBuilder.buildOpinionPrompt(memoryEntry);
        try {
            opinionEntry = llmResponseParser.parseOpinionFromResponse(llmClient.generateOpinionResponse(prompt), memoryEntry);
        } catch (Exception e) {
            System.out.println("Erreur de parsing d'opinion : " + e.getMessage());
            return null;
        }
        return opinionEntry;
    }

    /**
     * Prend en paramètre un souvenir et gère la création/l'update d'opinion
     *
     * @param memory Le souvenir source
     */

    public List<OpinionEntry> processInteraction(MemoryEntry memory) {
        OpinionEntry opinionEntry = getOpinionFromMemoryEntry(memory);
        List<OpinionEntry> opinionEntries = new ArrayList<>();
        if (opinionEntry == null) {
            return null;    //no opinion to process
        }

        // Logique de tri d'opinion
        List<SearchResult> similarOpinions = memoryService.searchOpinions(opinionEntry.getSubject());
        boolean similarOpinionsFound = false;

        for (SearchResult searchResult : similarOpinions) {

            if (searchResult.getSimilarityScore() >= 0.85) {            //TODO HANDLE LESSER SIMILARITY ? HANDLE SUBJECT DIFFERENTIATION
                similarOpinionsFound = true;
                opinionEntries.add(updateOpinion(searchResult, opinionEntry));
            }
        }
        if (!similarOpinionsFound) {
            opinionEntries.add(addOpinion(opinionEntry, memory));
        }
        return opinionEntries;
    }

    private double calculateDeltaC(OpinionEntry opinionEntry, double networkConsistency, double imp, int sOld, int sExp) {
        double deltaC;
        if (sOld == 0) {
            deltaC = REINFORCE_BASE * imp * (1.0 - opinionEntry.getConfidence()) * (0.75 + 0.5 * ((networkConsistency + 1.0) / 2.0));
        } else if (sOld == sExp) {
            // reinforce, amplified by positive networkConsistency
            double netFactor = 1.0 + 0.5 * Math.max(0.0, networkConsistency); // 1..1.5
            deltaC = REINFORCE_BASE * imp * (1.0 - opinionEntry.getConfidence()) * netFactor;
        } else {
            // contradiction, penalise more if networkConsistency negative
            double netFactor = 1.0 + 0.5 * Math.max(0.0, -networkConsistency); // 1..1.5
            deltaC = -CONTRADICT_BASE * imp * opinionEntry.getConfidence() * netFactor;
        }
        return deltaC;
    }

    private double updateStabilityScore(OpinionEntry opinionEntry, double networkConsistency, double imp, OpinionEntry newOpinion) {
        double expEffective = clamp((1.0 - RHO_NETWORK) * clamp(opinionEntry.getPolarity(), -1.0, 1.0) + RHO_NETWORK * clamp(networkConsistency, -1.0, 1.0), -1.0, 1.0);
        int sOld = sign(newOpinion.getPolarity());
        int sExp = sign(expEffective);
        double signDelta = (sOld == sExp ? +1.0 : -1.0);

        double deltaC = calculateDeltaC(opinionEntry, networkConsistency, imp, sOld, sExp);

        if (sOld == 0) signDelta = +0.5;
        double netStabFactor = 1.0 + 0.3 * ((networkConsistency + 1.0) / 2.0); // 1..1.3
        double stabilityDelta = (signDelta > 0 ? STABILITY_GROWTH : -STABILITY_SHRINK) * imp * Math.abs(deltaC) * netStabFactor;
        return clamp(opinionEntry.getStability() + stabilityDelta, 0.0, 1.0);
    }


    private double updateConfidenceScore(OpinionEntry opinionEntry, double networkConsistency, double imp) {
        double expEffective = clamp((1.0 - RHO_NETWORK) * clamp(opinionEntry.getPolarity(), -1.0, 1.0) + RHO_NETWORK * clamp(networkConsistency, -1.0, 1.0), -1.0, 1.0);
        int sOld = sign(opinionEntry.getPolarity());
        int sExp = sign(expEffective);
        double deltaC = calculateDeltaC(opinionEntry, networkConsistency, imp, sOld, sExp);

        return clamp(opinionEntry.getConfidence() + deltaC, 0.0, 1.0);

    }

    private double updatePolarityScore(OpinionEntry opinionEntry, double networkConsistency, double coh) {
        double expEffective = clamp((1.0 - RHO_NETWORK) * clamp(opinionEntry.getPolarity(), -1.0, 1.0) + RHO_NETWORK * clamp(networkConsistency, -1.0, 1.0), -1.0, 1.0);
        double experienceComponent = W_EXPERIENCE * expEffective + W_COHERENCE * coh;
        return clamp(opinionEntry.getPolarity() * opinionEntry.getStability() + (1.0 - opinionEntry.getStability()) * experienceComponent, -1.0, 1.0);
    }

    private double getNetworkConsistencyScore(OpinionEntry newOpinionEntry) {
        double mainValueScore;
        DimensionSchwartz mainValue;
        Map.Entry<DimensionSchwartz, Double> maxEntry = valueProfile.averageByDimension().entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (maxEntry == null) {
            System.out.println("Erreur null lors du calcul de normVp dans le module OpinionService");
            return 0.5;
        } else {
            mainValueScore = maxEntry.getValue();
            mainValue = maxEntry.getKey();
        }

        double normVp = (mainValueScore - 50.0) / 50.0;
        return normVp * newOpinionEntry.getPolarity();
    }

    private OpinionEntry updateOpinion(SearchResult searchResult, OpinionEntry newOpinion) {
        OpinionEntry opinionEntry = searchResult.getOpinionEntry();

        double networkConsistencyScore = getNetworkConsistencyScore(newOpinion);
        double newOpinionImportance = valueProfile.averageByDimension(opinionEntry.getMainDimension());
        double coherency = (valueProfile.averageByDimension(opinionEntry.getMainDimension()) - valueProfile.dimensionAverage()) / 100;

        opinionEntry.setStability(updateStabilityScore(opinionEntry, networkConsistencyScore, newOpinionImportance, newOpinion));
        if (opinionEntry.getStability() <= 0) {
            memoryService.deleteOpinion(opinionEntry.getId());
            return null; //Opinion deleted -> nothing more to do
        }
        opinionEntry.setConfidence(updateConfidenceScore(opinionEntry, networkConsistencyScore, newOpinionImportance));
        opinionEntry.setPolarity(updatePolarityScore(opinionEntry, networkConsistencyScore, coherency));


        memoryService.storeOpinion(opinionEntry);
        return opinionEntry;
    }


    private double calculateStabilityScore(OpinionEntry opinionEntry) {

        if (opinionEntry.getMainDimension() == null) {
            return 0.5;
        }
        return 0.5 + (valueProfile.averageByDimension(opinionEntry.getMainDimension()) / 200.0);

    }

    private OpinionEntry addOpinion(OpinionEntry opinionEntry, MemoryEntry associatedMemoryEntry) {
        opinionEntry.setId(UUID.randomUUID().toString());
        opinionEntry.setStability(calculateStabilityScore(opinionEntry));
        opinionEntry.setAssociatedMemories(List.of(associatedMemoryEntry.getId()));

        opinionEntry.setCreatedAt(LocalDateTime.now());
        opinionEntry.setUpdatedAt(LocalDateTime.now());

        memoryService.storeOpinion(opinionEntry);
        return opinionEntry;

    }
}
