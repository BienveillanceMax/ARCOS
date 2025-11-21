# Rapport Complet des Changements : Système d'Humeur et Initiatives

## 1. Introduction

Ce rapport détaille les modifications apportées pour doter l'IA d'une intelligence émotionnelle à court terme (Mood System) et réactiver sa capacité d'action autonome (Initiative System). Ces changements visent à rendre l'IA plus "vivante", réactive émotionnellement, et capable d'agir sur ses propres désirs.

## 2. Système d'Humeur Dynamique (Optimisé)

### Concept : Modèle PAD
Nous avons implémenté le modèle émotionnel **PAD** (Pleasure, Arousal, Dominance) qui représente l'humeur comme un point dans un espace tridimensionnel. Les émotions (Moods) sont des zones spécifiques de cet espace. Nous avons ajouté 4 nouvelles émotions complexes : **Honte**, **Culpabilité**, **Mépris**, **Surprise**.

### Flux Input -> Output (Optimisé : 1 Appel LLM)

1.  **Input Utilisateur** : L'utilisateur envoie un message (ex: "Tu as fait une erreur stupide !").
2.  **Capture (Orchestrator)** : `Orchestrator` intercepte le message.
3.  **Prompt Composite** :
    *   `PromptBuilder` génère un prompt conversationnel incluant l'humeur actuelle.
    *   Il ajoute une **instruction système** demandant une réponse au format **JSON**, contenant à la fois la réponse textuelle (`response`) et les deltas émotionnels (`mood_update`).
4.  **Génération Unique (LLMClient)** :
    *   `LLMClient.generateConversationResponse` appelle le modèle une seule fois.
    *   Le modèle analyse l'input, formule sa réponse et évalue son impact émotionnel simultanément.
5.  **Traitement de la Réponse** :
    *   L'`Orchestrator` reçoit l'objet `ConversationResponse`.
    *   **Texte** : La partie `response` est envoyée au module TTS (`Piper`) pour être prononcée.
    *   **Humeur** : La partie `mood_update` est passée à `MoodService.applyMoodUpdate` pour mettre à jour l'état PAD.
    *   **Mémoire** : La réponse textuelle est ajoutée à l'historique de conversation.

Cette approche optimise la latence en évitant un appel LLM dédié à l'analyse de sentiment.

### Tests Effectués
*   **Unitaires** : `PadStateTest`, `MoodTest`, `MoodServiceTest` (vérification de l'application des mises à jour).
*   **Intégration** : `MoodIntegrationTest` vérifie que le prompt demande bien du JSON et que le service applique correctement les objets de mise à jour.

## 3. Réactivation de l'Initiative (InitiativeService)

### Concept : Agents Autonomes
Le service d'initiative a été réécrit pour utiliser les capacités d'agents ("Function Calling") de Spring AI. Au lieu d'un planificateur rigide, nous donnons au LLM un objectif (Désir) et des outils, et nous le laissons décider des actions.

### Flux Input -> Output -> Feedback (Boucle BDI)

1.  **Déclencheur (Event)** : Un événement `INITIATIVE` est reçu (ex: "Je veux en savoir plus sur la météo").
2.  **Dispatch (Orchestrator)** : L'`Orchestrator` appelle `InitiativeService.processInitiative`.
3.  **Exécution (LLMClient)** :
    *   `PromptBuilder` crée un prompt d'initiative.
    *   `LLMClient` exécute le prompt avec les outils activés (`SearchActions`, etc.).
    *   Le modèle effectue les actions et renvoie un résultat final.
4.  **Mise à Jour (Output)** :
    *   Le désir est marqué comme `SATISFIED`.
    *   Le résultat est stocké dans le désir.
5.  **Boucle BDI (Feedback)** :
    *   `InitiativeService` crée un **Souvenir Explicite** (`MemoryEntry`) de l'action : "J'ai pris l'initiative... Résultat : ...".
    *   Ce souvenir est envoyé à `PersonalityOrchestrator.processExplicitMemory`.
    *   Cela déclenche immédiatement la formation d'opinions sur cette action, fermant la boucle cognitive (Désir -> Action -> Souvenir -> Opinion -> Désir).

### Tests Effectués
*   **Unitaires** : `InitiativeServiceTest` vérifie l'exécution de l'initiative, la mise à jour du désir, et surtout l'appel à `processExplicitMemory` pour fermer la boucle.

## 4. Fichiers Modifiés

*   `src/main/java/Personality/Mood/` (`Mood.java`, `PadState.java`, `MoodService.java`, `ConversationResponse.java`)
*   `src/main/java/LLM/Prompts/PromptBuilder.java` (Instructions JSON, Mood injection)
*   `src/main/java/LLM/LLMClient.java` (Nouvelle méthode `generateConversationResponse`)
*   `src/main/java/Orchestrator/Orchestrator.java` (Gestion de la réponse composite)
*   `src/main/java/Orchestrator/InitiativeService.java` (Boucle BDI)
*   `src/main/java/Personality/PersonalityOrchestrator.java` (Nouvelle méthode publique `processExplicitMemory`)
*   `src/test/java/...` (Tests mis à jour)

## 5. Modulation Vocale (TTS)

### Concept
Pour renforcer l'immersion, la voix de l'IA change dynamiquement en fonction de son état émotionnel (PAD).
Nous utilisons les paramètres de **Piper TTS** :
- **Length Scale (Vitesse)** : Lié à l'Arousal.
    - Arousal élevé (Excitation/Colère) -> Vitesse rapide (0.8).
    - Arousal bas (Tristesse/Ennui) -> Vitesse lente (1.2).
- **Noise Scale / Noise W** : Modulés légèrement pour varier le timbre.

### Implémentation
- `MoodVoiceMapper` : Convertit `PadState` en paramètres TTS.
- `PiperEmbeddedTTSModule` : Accepte désormais des paramètres dynamiques.
- `Orchestrator` : Calcule les paramètres juste avant la synthèse vocale.
