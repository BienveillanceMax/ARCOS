package org.arcos.Tools.GdeltTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltAnalysisService {

    private final PersonaTreeService personaTreeService;
    private final GdeltProperties properties;

    public GdeltAnalysisService(PersonaTreeService personaTreeService,
                                GdeltProperties properties) {
        this.personaTreeService = personaTreeService;
        this.properties = properties;
    }

    /**
     * Mode briefing : utilise le UserModel pour determiner les sujets pertinents.
     */
    public String generateBriefing() {
        int leafCount = personaTreeService.getNonEmptyLeafCount();
        log.info("Briefing requested — {} user profile leaves available", leafCount);
        return "Le service d'analyse GDELT n'est pas encore implemente. "
             + leafCount + " elements du profil utilisateur disponibles pour le briefing.";
    }

    /**
     * Mode analyse : analyse approfondie d'un sujet specifique.
     */
    public String analyzeSubject(String subject) {
        log.info("Deep analysis requested for subject: {}", subject);
        return "Le service d'analyse GDELT n'est pas encore implemente. "
             + "Sujet demande : " + subject;
    }
}
