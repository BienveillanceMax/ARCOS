package org.arcos.Tools.GdeltTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.GdeltThemeIndex.GdeltKeyword;
import org.arcos.UserModel.GdeltThemeIndex.GdeltThemeIndexGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltAnalysisService {

    private final GdeltThemeIndexGate themeIndexGate;
    private final GdeltProperties properties;

    public GdeltAnalysisService(GdeltThemeIndexGate themeIndexGate,
                                GdeltProperties properties) {
        this.themeIndexGate = themeIndexGate;
        this.properties = properties;
    }

    /**
     * Mode briefing : utilise le UserModel pour determiner les sujets pertinents.
     */
    public String generateBriefing() {
        List<GdeltKeyword> keywords = themeIndexGate.getAllKeywords();

        if (keywords.isEmpty()) {
            log.info("Briefing requested but no GDELT keywords indexed yet");
            return "Aucun centre d'interet indexe pour le briefing. "
                 + "Le profil utilisateur ne contient pas encore assez d'informations "
                 + "sur les centres d'interet, croyances ou engagements.";
        }

        log.info("Briefing requested — {} GDELT keywords available from {} indexed leaves",
                keywords.size(), themeIndexGate.getIndexedLeafCount());

        String keywordList = keywords.stream()
                .map(k -> k.language().name() + ":" + k.term())
                .collect(Collectors.joining(", "));

        // TODO: Phase suivante — utiliser ces keywords pour appeler l'API GDELT DOC 2.0
        return "Mots-cles d'interet utilisateur indexes : " + keywordList
             + ". L'appel a l'API GDELT DOC 2.0 n'est pas encore implemente.";
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
