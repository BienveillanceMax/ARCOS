package org.arcos.IntegrationTests.Services;

import org.arcos.E2E.E2ETestConfig;
import org.arcos.UserModel.DfsNavigator.*;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests d'integration pour le DfsNavigatorService.
 *
 * Verifie :
 * - AC1 : scoring des branches L1 et selection top-N
 * - AC2 : drill-down L2 et collecte des feuilles au-dessus du seuil
 * - AC3 : degradation gracieuse quand le modele ONNX cross-encoder est absent/incompatible
 * - AC4 : formatage markdown par UserContextFormatter pour injection dans le prompt
 *
 * Le CrossEncoderService ONNX ne peut pas charger le modele (IR version 10 > runtime max 9),
 * donc les tests AC1/AC2 injectent un mock CrossEncoderService pour simuler le scoring.
 * Le test AC3 utilise le comportement naturel (model absent -> degradation gracieuse).
 *
 * Pre-requis : docker compose up qdrant
 */
@SpringBootTest(properties = {
        "arcos.user-model.idle-threshold-minutes=60",
        "arcos.user-model.session-end-threshold-minutes=5",
        "arcos.user-model.dfs-top-n-l1=3",
        "arcos.user-model.dfs-l2-threshold=0.0"
})
@ActiveProfiles("test-e2e")
@Import(E2ETestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DfsNavigatorIT {

    @Autowired private DfsNavigatorService dfsNavigatorService;
    @Autowired private CrossEncoderService crossEncoderService;
    @Autowired private BranchDescriptionRegistry registry;
    @Autowired private UserContextFormatter userContextFormatter;
    @Autowired private PersonaTreeGate personaTreeGate;
    @Autowired private PersonaTreeService personaTreeService;

    // Sauvegarde du vrai CrossEncoderService pour restauration
    private CrossEncoderService realCrossEncoder;

    /**
     * Injecte quelques feuilles dans le PersonaTree pour que les tests
     * puissent verifier la collecte de feuilles par le DFS.
     */
    @BeforeAll
    void populateTestData() {
        // Remplir quelques feuilles dans Physical_Appearance
        safeSetLeaf("1_Biological_Characteristics.Physical_Appearance.Body_Build.Height", "180cm");
        safeSetLeaf("1_Biological_Characteristics.Physical_Appearance.Body_Build.Weight", "75kg");
        safeSetLeaf("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brun, courts");

        // Remplir des feuilles dans Cognitive_Abilities
        safeSetLeaf("2_Psychological_Characteristics.Cognitive_Abilities.Learning_Ability.Learning_Style", "visuel");

        // Remplir des feuilles dans Biological_Rhythms (terminal L1, pas de L2)
        safeSetLeaf("1_Biological_Characteristics.Biological_Rhythms.Sleep_Characteristics", "couche-tard, 7h de sommeil");

        // Remplir des feuilles dans Social_Identity
        safeSetLeaf("4_Identity_Characteristics.Social_Identity.Occupational_Role.Job_Title", "ingenieur logiciel");
    }

    @BeforeEach
    void setUp() {
        // Sauvegarder le vrai CrossEncoderService
        realCrossEncoder = (CrossEncoderService) ReflectionTestUtils.getField(dfsNavigatorService, "crossEncoder");
    }

    @AfterEach
    void restoreReal() {
        // Restaurer le vrai CrossEncoderService apres chaque test
        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", realCrossEncoder);
    }

    // ========================================================================
    // AC1: L1 branch scoring — le DFS selectionne les top-N branches L1
    // ========================================================================

    @Test
    @Order(1)
    void navigate_shouldSelectTopNL1Branches_whenCrossEncoderScoresProvided() {
        // Given: mock du CrossEncoderService pour simuler des scores L1
        CrossEncoderService mockCrossEncoder = Mockito.mock(CrossEncoderService.class);
        when(mockCrossEncoder.isAvailable()).thenReturn(true);

        // 29 branches L1 — on fait scorer Physical_Appearance, Cognitive_Abilities et Social_Identity
        // comme les 3 plus pertinents (top-N = 3)
        float[] l1Scores = new float[29];
        l1Scores[0] = 5.0f;   // Physical_Appearance (index 0)
        l1Scores[4] = 4.0f;   // Cognitive_Abilities (index 4)
        l1Scores[14] = 3.0f;  // Social_Identity (index 14)
        // Tous les autres restent a 0.0

        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 29)))
                .thenReturn(l1Scores);

        // Scores L2 : retourner des scores positifs pour toutes les sous-branches
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() != 29)))
                .thenAnswer(invocation -> {
                    List<String> descriptions = invocation.getArgument(1);
                    float[] scores = new float[descriptions.size()];
                    for (int i = 0; i < scores.length; i++) {
                        scores[i] = 1.0f; // tous au-dessus du seuil 0.0
                    }
                    return scores;
                });

        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", mockCrossEncoder);

        // When
        DfsResult result = dfsNavigatorService.navigate("je fais du sport et je travaille en informatique");

        // Then
        assertNotNull(result);
        assertEquals(3, result.selectedL1Branches().size(),
                "Le DFS devrait selectionner exactement 3 branches L1 (top-N = 3)");
        assertTrue(result.selectedL1Branches().contains("Physical_Appearance"),
                "Physical_Appearance devrait etre selectionne (score le plus eleve)");
        assertTrue(result.selectedL1Branches().contains("Cognitive_Abilities"),
                "Cognitive_Abilities devrait etre selectionne");
        assertTrue(result.selectedL1Branches().contains("Social_Identity"),
                "Social_Identity devrait etre selectionne");

        // L'ordre devrait etre par score decroissant
        assertEquals("Physical_Appearance", result.selectedL1Branches().get(0),
                "Physical_Appearance devrait etre en premiere position (score 5.0)");

        // Verification que le cross-encoder a ete appele avec les 29 descriptions L1
        verify(mockCrossEncoder).score(
                eq("je fais du sport et je travaille en informatique"),
                argThat(list -> list != null && list.size() == 29));
    }

    @Test
    @Order(2)
    void navigate_shouldScoreAll29L1Branches() {
        // Given: verifier que le registry contient bien 29 branches L1
        List<String> l1Descriptions = registry.getL1Descriptions();
        assertEquals(29, l1Descriptions.size(),
                "Le registry devrait contenir 29 branches L1");

        // Avec un mock cross-encoder qui retourne des scores
        CrossEncoderService mockCrossEncoder = Mockito.mock(CrossEncoderService.class);
        when(mockCrossEncoder.isAvailable()).thenReturn(true);

        float[] l1Scores = new float[29];
        // Un seul score haut pour verifier la selection
        l1Scores[2] = 10.0f; // Biological_Rhythms
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 29)))
                .thenReturn(l1Scores);
        // Scores L2 par defaut
        lenient().when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() != 29)))
                .thenReturn(new float[0]);

        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", mockCrossEncoder);

        // When
        DfsResult result = dfsNavigatorService.navigate("mes cycles de sommeil");

        // Then: Biological_Rhythms devrait etre dans les branches selectionnees
        assertTrue(result.selectedL1Branches().contains("Biological_Rhythms"),
                "Biological_Rhythms devrait etre selectionne quand il a le score le plus eleve");
        assertTrue(result.latencyMs() >= 0,
                "La latence devrait etre positive");
    }

    // ========================================================================
    // AC2: L2 drill-down — collecte des feuilles au-dessus du seuil
    // ========================================================================

    @Test
    @Order(3)
    void navigate_shouldDrillIntoL2AndCollectLeaves_whenThresholdMet() {
        // Given: mock cross-encoder avec Physical_Appearance en top-1
        CrossEncoderService mockCrossEncoder = Mockito.mock(CrossEncoderService.class);
        when(mockCrossEncoder.isAvailable()).thenReturn(true);

        float[] l1Scores = new float[29];
        l1Scores[0] = 10.0f; // Physical_Appearance (a 4 sous-branches L2)
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 29)))
                .thenReturn(l1Scores);

        // L2 de Physical_Appearance : Body_Build (1.0), Facial (-1.0), Skin (-1.0), Hair (2.0)
        // Avec threshold=0.0, Body_Build et Hair passent, Facial et Skin non
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 4)))
                .thenReturn(new float[]{1.0f, -1.0f, -1.0f, 2.0f});

        // L2 pour les 2 autres branches top-3 (scores a 0, donc ils apparaissent)
        lenient().when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() != 29 && list.size() != 4)))
                .thenAnswer(invocation -> {
                    List<String> descs = invocation.getArgument(1);
                    float[] scores = new float[descs.size()];
                    // Scores a -1 pour ne pas collecter de feuilles des autres branches
                    for (int i = 0; i < scores.length; i++) scores[i] = -1.0f;
                    return scores;
                });

        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", mockCrossEncoder);

        // When
        DfsResult result = dfsNavigatorService.navigate("ma morphologie et mes cheveux");

        // Then: les sous-branches L2 selectionnees devraient inclure Body_Build et Hair
        assertTrue(result.selectedL2Branches().contains("Body_Build"),
                "Body_Build devrait etre selectionne (score 1.0 >= seuil 0.0)");
        assertTrue(result.selectedL2Branches().contains("Hair"),
                "Hair devrait etre selectionne (score 2.0 >= seuil 0.0)");
        assertFalse(result.selectedL2Branches().contains("Facial_Features"),
                "Facial_Features ne devrait pas etre selectionne (score -1.0 < seuil 0.0)");
        assertFalse(result.selectedL2Branches().contains("Skin"),
                "Skin ne devrait pas etre selectionne (score -1.0 < seuil 0.0)");

        // Les feuilles collectees devraient inclure celles de Body_Build et Hair
        Map<String, String> leaves = result.relevantLeaves();
        assertTrue(leaves.containsKey("1_Biological_Characteristics.Physical_Appearance.Body_Build.Height"),
                "La feuille Height devrait etre collectee sous Body_Build");
        assertTrue(leaves.containsKey("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair"),
                "La feuille Scalp_Hair devrait etre collectee sous Hair");
    }

    @Test
    @Order(4)
    void navigate_shouldCollectLeavesDirectly_forTerminalL1WithoutL2() {
        // Given: Biological_Rhythms n'a pas de groupe L2 (terminal)
        CrossEncoderService mockCrossEncoder = Mockito.mock(CrossEncoderService.class);
        when(mockCrossEncoder.isAvailable()).thenReturn(true);

        float[] l1Scores = new float[29];
        l1Scores[2] = 10.0f; // Biological_Rhythms — pas de L2
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 29)))
                .thenReturn(l1Scores);

        // Les autres branches L1 en top-3 auront des L2 — scores negatifs
        lenient().when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() != 29)))
                .thenAnswer(invocation -> {
                    List<String> descs = invocation.getArgument(1);
                    float[] scores = new float[descs.size()];
                    for (int i = 0; i < scores.length; i++) scores[i] = -1.0f;
                    return scores;
                });

        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", mockCrossEncoder);

        // When
        DfsResult result = dfsNavigatorService.navigate("je dors mal en ce moment");

        // Then: les feuilles de Biological_Rhythms devraient etre collectees directement
        assertTrue(result.selectedL1Branches().contains("Biological_Rhythms"),
                "Biological_Rhythms devrait etre selectionne");
        assertTrue(result.relevantLeaves().containsKey(
                        "1_Biological_Characteristics.Biological_Rhythms.Sleep_Characteristics"),
                "La feuille Sleep_Pattern devrait etre collectee directement (L1 terminal)");
        assertEquals("couche-tard, 7h de sommeil",
                result.relevantLeaves().get("1_Biological_Characteristics.Biological_Rhythms.Sleep_Characteristics"));
    }

    @Test
    @Order(5)
    void navigate_shouldFilterL2BelowThreshold() {
        // Given: on change le seuil L2 a 1.5 pour ce test via un nouveau DfsNavigatorService
        // avec un seuil plus strict
        CrossEncoderService mockCrossEncoder = Mockito.mock(CrossEncoderService.class);
        when(mockCrossEncoder.isAvailable()).thenReturn(true);

        // On injecte temporairement un seuil L2 plus eleve
        float originalThreshold = (float) ReflectionTestUtils.getField(dfsNavigatorService, "l2Threshold");
        ReflectionTestUtils.setField(dfsNavigatorService, "l2Threshold", 1.5f);
        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", mockCrossEncoder);

        try {
            float[] l1Scores = new float[29];
            l1Scores[0] = 10.0f; // Physical_Appearance
            when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 29)))
                    .thenReturn(l1Scores);

            // L2 scores: seul Hair (2.0) depasse le seuil de 1.5
            when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 4)))
                    .thenReturn(new float[]{0.5f, 0.2f, -0.3f, 2.0f});

            // Autres branches L2
            lenient().when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() != 29 && list.size() != 4)))
                    .thenAnswer(invocation -> {
                        List<String> descs = invocation.getArgument(1);
                        float[] scores = new float[descs.size()];
                        for (int i = 0; i < scores.length; i++) scores[i] = -1.0f;
                        return scores;
                    });

            // When
            DfsResult result = dfsNavigatorService.navigate("mes cheveux");

            // Then: seul Hair devrait passer le seuil de 1.5
            assertTrue(result.selectedL2Branches().contains("Hair"),
                    "Hair devrait passer le seuil (score 2.0 >= 1.5)");
            assertFalse(result.selectedL2Branches().contains("Body_Build"),
                    "Body_Build ne devrait pas passer le seuil (score 0.5 < 1.5)");
        } finally {
            // Restaurer le seuil original
            ReflectionTestUtils.setField(dfsNavigatorService, "l2Threshold", originalThreshold);
        }
    }

    // ========================================================================
    // AC3: Degradation gracieuse — ONNX cross-encoder absent/incompatible
    // ========================================================================

    @Test
    @Order(6)
    void navigate_shouldReturnEmptyResult_whenOnnxModelNotAvailable() {
        // Given: le vrai CrossEncoderService avec le modele ONNX qui ne peut pas se charger
        // (IR version 10 incompatible avec le runtime, ou modele absent)
        // On utilise le vrai CrossEncoderService (pas de mock)

        // When: le navigate ne doit PAS crasher
        DfsResult result = assertDoesNotThrow(
                () -> dfsNavigatorService.navigate("une requete quelconque"),
                "Le DFS ne devrait pas crasher quand le modele ONNX est indisponible");

        // Then: resultat vide mais valide
        assertNotNull(result, "Le resultat ne devrait pas etre null");
        assertTrue(result.relevantLeaves().isEmpty(),
                "Les feuilles devraient etre vides quand le cross-encoder est indisponible");
        assertTrue(result.selectedL1Branches().isEmpty(),
                "Les branches L1 devraient etre vides");
        assertTrue(result.selectedL2Branches().isEmpty(),
                "Les branches L2 devraient etre vides");
        assertTrue(result.latencyMs() >= 0,
                "La latence devrait etre positive");
    }

    @Test
    @Order(7)
    void crossEncoderService_shouldReportNotAvailable_whenModelCannotLoad() {
        // Given: le vrai CrossEncoderService dans le contexte Spring
        // Le modele ONNX IR version 10 ne peut pas se charger (runtime supporte max IR 9)

        // When/Then
        assertFalse(crossEncoderService.isAvailable(),
                "Le CrossEncoderService devrait signaler indisponible quand le modele ONNX ne charge pas");
    }

    @Test
    @Order(8)
    void crossEncoderService_shouldReturnEmptyScores_whenNotAvailable() {
        // Given: le vrai CrossEncoderService (indisponible)
        assertFalse(crossEncoderService.isAvailable());

        // When
        float[] scores = crossEncoderService.score("test", List.of("desc1", "desc2", "desc3"));

        // Then
        assertEquals(0, scores.length,
                "score() devrait retourner un tableau vide quand le service est indisponible");
    }

    @Test
    @Order(9)
    void navigate_shouldNotThrow_withRepeatedCallsWhenModelAbsent() {
        // Given: modele absent, appels repetes ne devraient pas accumuler d'erreurs

        // When/Then: 10 appels successifs sans crash
        for (int i = 0; i < 10; i++) {
            DfsResult result = assertDoesNotThrow(
                    () -> dfsNavigatorService.navigate("requete " + System.currentTimeMillis()),
                    "L'appel #" + i + " ne devrait pas crasher");
            assertNotNull(result);
            assertTrue(result.relevantLeaves().isEmpty());
        }
    }

    // ========================================================================
    // AC4: UserContextFormatter — formatage markdown pour injection prompt
    // ========================================================================

    @Test
    @Order(10)
    void format_shouldProduceMarkdownWithHeader() {
        // Given: des feuilles collectees
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brun, courts");
        leaves.put("4_Identity_Characteristics.Social_Identity.Occupational_Role.Job_Title", "ingenieur logiciel");

        // When
        String markdown = userContextFormatter.format(leaves);

        // Then
        assertNotNull(markdown);
        assertTrue(markdown.startsWith("## Profil utilisateur\n"),
                "Le markdown devrait commencer par un header '## Profil utilisateur'");
        assertTrue(markdown.contains("- "),
                "Le markdown devrait contenir des items en liste a puces");
        assertTrue(markdown.contains(" : brun, courts"),
                "Le markdown devrait contenir la valeur de la feuille cheveux");
        assertTrue(markdown.contains(" : ingenieur logiciel"),
                "Le markdown devrait contenir la valeur de la feuille metier");
    }

    @Test
    @Order(11)
    void format_shouldUseHumanReadablePathsFromRegistry() {
        // Given
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brun");

        // When
        String markdown = userContextFormatter.format(leaves);

        // Then: le path devrait etre traduit en human-readable via le registry
        // Physical_Appearance -> "Apparence physique", Hair -> "Cheveux et pilosite"
        assertTrue(markdown.contains("Apparence physique"),
                "Le nom L1 devrait etre traduit en francais depuis le registry");
        assertTrue(markdown.contains("Cheveux"),
                "Le nom L2 devrait etre traduit (debut de 'Cheveux et pilosite')");
        // Ne devrait PAS contenir les cles brutes
        assertFalse(markdown.contains("Physical_Appearance"),
                "Les cles brutes ne devraient pas apparaitre dans le markdown formate");
    }

    @Test
    @Order(12)
    void format_shouldReturnEmptyString_forEmptyLeaves() {
        // Given/When
        String result = userContextFormatter.format(Map.of());

        // Then
        assertEquals("", result, "Un map vide devrait produire une chaine vide");
    }

    @Test
    @Order(13)
    void format_shouldReturnEmptyString_forNullLeaves() {
        // Given/When
        String result = userContextFormatter.format(null);

        // Then
        assertEquals("", result, "null devrait produire une chaine vide");
    }

    @Test
    @Order(14)
    void format_shouldHandleMultipleLeavesFromDifferentBranches() {
        // Given: feuilles de plusieurs branches L1 differentes
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Biological_Characteristics.Physical_Appearance.Body_Build.Height", "180cm");
        leaves.put("1_Biological_Characteristics.Biological_Rhythms.Sleep_Characteristics", "couche-tard");
        leaves.put("2_Psychological_Characteristics.Cognitive_Abilities.Learning_Ability.Learning_Style", "visuel");
        leaves.put("4_Identity_Characteristics.Social_Identity.Occupational_Role.Job_Title", "ingenieur");

        // When
        String markdown = userContextFormatter.format(leaves);

        // Then: chaque feuille devrait etre presente
        String[] lines = markdown.split("\n");
        // Header + 4 items = 5 lignes (ou plus si la derniere a un newline)
        assertTrue(lines.length >= 5,
                "Le markdown devrait contenir au moins 5 lignes (header + 4 feuilles)");
        assertTrue(markdown.contains("180cm"), "Devrait contenir la taille");
        assertTrue(markdown.contains("couche-tard"), "Devrait contenir le rythme de sommeil");
        assertTrue(markdown.contains("visuel"), "Devrait contenir le style d'apprentissage");
        assertTrue(markdown.contains("ingenieur"), "Devrait contenir le metier");
    }

    @Test
    @Order(15)
    void format_outputShouldBeSuitableForPromptInjection() {
        // Given: un resultat DFS simule
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brun, courts");

        // When
        String markdown = userContextFormatter.format(leaves);

        // Then: le format doit etre injectable dans un prompt LLM
        // - Pas de caracteres de controle (sauf newlines)
        // - Utilise du markdown standard
        // - Chaque ligne de contenu commence par "- " (liste a puces)
        assertFalse(markdown.contains("\t"), "Pas de tabulations");
        assertTrue(markdown.contains("##"), "Devrait utiliser un header markdown");

        String[] contentLines = markdown.split("\n");
        for (int i = 1; i < contentLines.length; i++) {
            if (!contentLines[i].isEmpty()) {
                assertTrue(contentLines[i].startsWith("- "),
                        "Chaque ligne de contenu devrait commencer par '- ' : " + contentLines[i]);
            }
        }
    }

    @Test
    @Order(16)
    void navigate_endToEnd_shouldProduceFormattableOutput() {
        // Given: mock cross-encoder pour obtenir un vrai resultat DFS
        CrossEncoderService mockCrossEncoder = Mockito.mock(CrossEncoderService.class);
        when(mockCrossEncoder.isAvailable()).thenReturn(true);

        float[] l1Scores = new float[29];
        l1Scores[0] = 10.0f; // Physical_Appearance
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() == 29)))
                .thenReturn(l1Scores);

        // Tous les L2 au-dessus du seuil
        when(mockCrossEncoder.score(anyString(), argThat(list -> list != null && list.size() != 29)))
                .thenAnswer(invocation -> {
                    List<String> descs = invocation.getArgument(1);
                    float[] scores = new float[descs.size()];
                    for (int i = 0; i < scores.length; i++) scores[i] = 1.0f;
                    return scores;
                });

        ReflectionTestUtils.setField(dfsNavigatorService, "crossEncoder", mockCrossEncoder);

        // When: navigate puis formater
        DfsResult result = dfsNavigatorService.navigate("mon apparence physique");
        String markdown = userContextFormatter.format(result.relevantLeaves());

        // Then: le pipeline complet fonctionne de bout en bout
        assertFalse(result.relevantLeaves().isEmpty(),
                "Le DFS devrait avoir collecte des feuilles (donnees peuplees en BeforeAll)");
        assertFalse(markdown.isEmpty(),
                "Le markdown ne devrait pas etre vide quand il y a des feuilles");
        assertTrue(markdown.startsWith("## Profil utilisateur"),
                "Le markdown devrait commencer par le header");
        assertTrue(markdown.contains("180cm") || markdown.contains("brun"),
                "Le markdown devrait contenir des valeurs peuplees dans le PersonaTree");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Ecrit une valeur dans une feuille du PersonaTree, en ignorant les erreurs
     * si le chemin n'existe pas dans le schema.
     */
    private void safeSetLeaf(String path, String value) {
        try {
            personaTreeService.setLeafValue(path, value);
        } catch (IllegalArgumentException e) {
            // Le chemin n'existe pas dans le schema — ignorer silencieusement
            // (le schema peut varier selon la version)
        }
    }
}
