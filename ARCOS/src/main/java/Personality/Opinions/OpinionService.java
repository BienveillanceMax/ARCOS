package Personality.Opinions;

import LLM.LLMClient;
import LLM.LLMResponseParser;
import LLM.Prompts.PromptBuilder;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;
import Memory.LongTermMemory.Models.SearchResult;
import Memory.LongTermMemory.service.EmbeddingGenerator;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Values.ValueProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
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

    public OpinionService(LLMResponseParser llmResponseParser, LLMClient llmClient, MemoryService memoryService, PromptBuilder promptBuilder, ValueProfile valueProfile) {
        this.llmResponseParser = llmResponseParser;
        this.llmClient = llmClient;
        this.memoryService = memoryService;
        this.promptBuilder = promptBuilder;
        this.valueProfile = valueProfile;
    }

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


    public void processInteraction(MemoryEntry memory) {
        OpinionEntry opinionEntry = getOpinionFromMemoryEntry(memory);
        if (opinionEntry == null) {
            return;
        }

        // Logique de tri d'opinion
        List<SearchResult> similarOpinions = memoryService.searchOpinions(opinionEntry.getSubject());
        boolean similarOpinionsFound = false;

        for (SearchResult searchResult : similarOpinions) {

            if (searchResult.getSimilarityScore() >= 0.85) {            //TODO HANDLE LESSER SIMILARITY ? HANDLE SUBJECT DIFFERENTIATION
                similarOpinionsFound = true;
                updateOpinion(searchResult);
            }
        }
        if (!similarOpinionsFound) {
            addOpinion(opinionEntry, memory);
        }
    }

    private double updateStabilityScore(OpinionEntry opinionEntry)
    {
        //todo logic
        return Math.min(1.0, opinionEntry.getStability());

    }

    private void updateOpinion(SearchResult searchResult) {
       OpinionEntry opinionEntry = searchResult.getOpinionEntry();
       //todo
        memoryService.storeOpinion(opinionEntry);

    }


    private double calculateStabilityScore(OpinionEntry opinionEntry) {

        if (opinionEntry.getMainDimension() == null) {
            return 0.5;
        }
        return 0.5 + (valueProfile.averageByDimension(opinionEntry.getMainDimension()) / 200.0);

    }

    private void addOpinion(OpinionEntry opinionEntry, MemoryEntry associatedMemoryEntry) {
        opinionEntry.setId(UUID.randomUUID().toString());
        opinionEntry.setStability(calculateStabilityScore(opinionEntry));
        opinionEntry.setAssociatedMemories(List.of(associatedMemoryEntry.getId()));

        opinionEntry.setCreatedAt(LocalDateTime.now());
        opinionEntry.setUpdatedAt(LocalDateTime.now());

        memoryService.storeOpinion(opinionEntry);


    }


}
