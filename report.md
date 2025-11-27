# Rapport d'Analyse et Propositions d'Évolution pour ArCoS

## 1. Analyse de l'Existant

Le système actuel (ArCoS) repose sur une boucle cognitive centrée sur l'IA elle-même, utilisant Spring AI 1.0.0 avec Qdrant comme mémoire vectorielle.

### Architecture Actuelle
*   **Mémoire (MemoryEntry)** : Stockage non structuré (texte + embedding) dans Qdrant. Idéal pour la recherche sémantique ("retrouver un concept"), mais faible pour la structure ("qui est X pour Y ?").
*   **Personnalité (Opinions & Désirs)** : Calcifer génère des opinions et des désirs basés sur ses propres valeurs (Schwartz) et l'intensité émotionnelle.
*   **Proactivité (DesireInitiativeProducer)** : Déclenchée par l'intensité des désirs internes. Elle est *réactive* aux états internes de l'IA, et non *proactive* vis-à-vis des besoins de l'utilisateur.

### Limitations pour un Assistant "Utile"
1.  **Cécité Utilisateur** : Le système ne construit pas de modèle explicite de l'utilisateur. Il ne "sait" pas qui vous êtes, il ne fait que "retrouver des conversations passées" où vous avez parlé de vous.
2.  **Manque de Contexte Temporel** : Aucune notion de routine ou d'habitude.
3.  **Absence de Raisonnement Relationnel** : Impossible de déduire des faits complexes (ex: "Si l'utilisateur aime X et que Y est un type de X, alors proposer Y").

---

## 2. Propositions d'Évolution

Voici trois architectures robustes pour transformer Calcifer, classées par complexité et impact.

### Proposition A : Le Système de Profilage Actif (User Modeling)
*L'approche la plus immédiate et rentable.*

**Concept :** Créer une "Mémoire Sémantique Structurée" dédiée à l'utilisateur, séparée de la mémoire épisodique (conversations).

**Implémentation Technique :**
*   **Extraction de Faits (Background Job)** : Utiliser un appel LLM asynchrone après chaque conversation pour extraire des faits sous forme JSON.
    *   *Input* : Conversation brute.
    *   *Prompt* : "Extrais les faits permanents sur l'utilisateur (goûts, relations, projets)."
    *   *Output* : `{"preferences": ["aime le café"], "goals": ["apprendre Java"]}`.
*   **Stockage** : Fichier JSON local (MVP) ou base de données légère (H2/SQLite) stockée dans `ARCOS/data/user_profile.json`.
*   **Injection** : Au démarrage d'une session, le `UserProfileService` charge ces données et les injecte dans le `SystemPrompt` de Calcifer.

**Avantages :**
*   Personnalisation immédiate.
*   Pas de nouvelle infrastructure lourde (utilise l'existant).
*   Réduit drastiquement les hallucinations sur la vie de l'utilisateur.

### Proposition B : Le Moteur Prédictif Temporel (Temporal Engine)
*Pour donner une "vie" et un rythme à l'IA.*

**Concept :** Analyser les métadonnées temporelles des interactions pour détecter des patterns.

**Implémentation Technique :**
*   **Event Logging** : Enrichir `MemoryEntry` ou créer une table `InteractionLog` avec `timestamp`, `intent` (détecté par LLM), et `user_state`.
*   **Routine Detection** : Un algorithme simple (ex: moyenne glissante) ou un petit modèle de clustering qui identifie les récurrences (ex: "Demande météo" souvent vers 08h00).
*   **Proactive Scheduler** : Un nouveau `Producer` (`RoutineInitiativeProducer`) qui vérifie chaque minute si une routine est attendue et génère une `Initiative` si l'utilisateur est silencieux.

**Avantages :**
*   L'IA semble "vivre" avec l'utilisateur.
*   Anticipation réelle des besoins (météo, agenda, rappel de médicaments).

### Proposition C : GraphRAG avec Neo4j (Architecture Avancée)
*Pour un raisonnement profond et contextuel.*

**Concept :** Remplacer ou augmenter Qdrant par une base de données Graphe (Neo4j) pour structurer la mémoire sous forme de nœuds et relations.

**Faisabilité Technique (Spring AI 1.0.0) :**
*   Spring AI supporte officiellement Neo4j comme `VectorStore`.
*   **Avantage Neo4j** : Il permet de stocker les *embeddings* sur les nœuds (Vector Search) tout en maintenant des *relations explicites* (Graph Search).
*   **Architecture Hybride** :
    *   *Nœuds* : `Concept`, `Personne`, `Event`, `Lieu`.
    *   *Relations* : `AIME`, `CONNAIT`, `EST_SITUÉ_À`.
    *   *Retrieval* : La recherche combine similarité vectorielle (trouver des concepts proches) et traversée de graphe (trouver les conséquences logiques).

**Implémentation :**
1.  Ajouter `spring-ai-starter-vector-store-neo4j`.
2.  Remplacer `QdrantVectorStore` par `Neo4jVectorStore`.
3.  Développer un `KnowledgeGraphBuilder` qui convertit les souvenirs en triplets (Sujet, Prédicat, Objet) pour les insérer dans le graphe.

**Avantages :**
*   Permet des réponses du type "Tu m'as dit hier que tu étais fatigué, donc je ne te propose pas de coder ce soir".
*   Exploite la puissance de GraphRAG (Retrieval Augmented Generation sur Graphe).

---

## 3. Recommandation Stratégique

Je recommande une approche incrémentale :

1.  **Phase 1 (Fondations - Proposition A)** : Implémenter le **Profilage Utilisateur (JSON/H2)**. C'est le prérequis indispensable pour que Calcifer sache "qui" il aide. Cela peut être fait rapidement avec le stack actuel.
2.  **Phase 2 (Comportement - Proposition B)** : Ajouter le **Moteur Temporel**. Une fois que l'IA connaît l'utilisateur, elle doit apprendre son rythme.
3.  **Phase 3 (Intelligence - Proposition C)** : Si le besoin de raisonnement complexe se fait sentir, migrer la mémoire vers **Neo4j/GraphRAG**. C'est un changement d'infrastructure majeur à réserver pour une étape ultérieure de maturation.

**Action Immédiate Suggérée :**
Démarrer la Phase 1 en créant un `UserProfileService` et un prompt d'extraction de faits.
