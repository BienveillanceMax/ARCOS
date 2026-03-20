# Design : GdeltThemeIndex — Source spécialisée du UserModel

**Date** : 2026-03-20
**Statut** : Validé — post-review architecturale (2 FAILs corrigés, 4 WARNs adressés)

---

## 1. Contexte

Le tool LLM `Rapport_Actualites` (`GdeltActions`) propose deux modes :
- **Briefing** : rapport d'actualité personnalisé basé sur le profil utilisateur
- **Analyse** : analyse géopolitique approfondie d'un sujet donné

Le mode briefing nécessite de savoir quels sujets d'actualité intéressent l'utilisateur. Cette information existe dans le PersonaTree sous forme de descriptions NL (ex: "Milite pour la transition écologique, suit les politiques environnementales européennes"), mais elle n'est pas directement utilisable comme requête de recherche GDELT.

**Problème** : transformer les valeurs NL des feuilles du PersonaTree en mots-clés de recherche actionnables par l'API GDELT DOC 2.0.

## 2. Décision architecturale

### Principe fondamental

Le UserModel est la **source de vérité** sur l'utilisateur. Il comprend :
- Une **source principale** : le PersonaTree (profil NL hiérarchique)
- Des **sources spécialisées** : des projections de données utilisateur formatées pour des consommations spécifiques

Le GdeltThemeIndex est une **source spécialisée du UserModel**, pas un composant du tool GDELT. Il représente les centres d'intérêt de l'utilisateur sous forme de keywords de recherche d'actualité.

### Direction des dépendances

```
Tools.GdeltTool.GdeltAnalysisService
         │
         │ reads (via gate)
         ▼
UserModel.GdeltThemeIndex.GdeltThemeIndexGate
         │
         │ (interne au UserModel)
         ▼
UserModel.PersonaTree.TreeOperationService
         │
         │ notifie lors de mutations de feuilles pertinentes
         ▼
UserModel.GdeltThemeIndex.GdeltThemeIndexService
```

Les dépendances pointent **toujours de la périphérie (Tools) vers le core (UserModel)**. Le UserModel ne connaît pas les Tools. À l'intérieur du UserModel, le PersonaTree et le GdeltThemeIndex sont des sous-packages frères qui se connaissent.

## 3. Feuilles GDELT-pertinentes

Seul un sous-ensemble des branches du PersonaTree produit des intérêts d'actualité exploitables. Cette liste est définie à la conception :

| L1 Branch | Tree Path Prefix | Justification |
|-----------|-----------------|---------------|
| `Life_Beliefs` | `4_Identity_Characteristics.Life_Beliefs` | Engagements politiques, causes sociales, vision du monde |
| `Motivations_and_Goals` | `4_Identity_Characteristics.Motivations_and_Goals` | Aspirations, projets qui orientent les intérêts |
| `Social_Identity.Occupational_Role` | `4_Identity_Characteristics.Social_Identity.Occupational_Role` | Secteur professionnel, veille métier |
| `Interests_and_Skills` | `5_Behavioral_Characteristics.Interests_and_Skills` | Domaines de connaissance, hobbies, compétences |
| `Knowledge_Base` | `5_Behavioral_Characteristics.Knowledge_Base` | Préférences culturelles, domaines d'expertise |
| `Social_Engagement` | `5_Behavioral_Characteristics.Social_Engagement` | Causes défendues, implication civique |

Lorsqu'une opération ADD, UPDATE ou DELETE est appliquée sur une feuille dont le path commence par l'un de ces préfixes, le `TreeOperationService` déclenche la mise à jour de l'index GDELT.

Les feuilles hors de ces préfixes (apparence physique, rythmes biologiques, santé, etc.) ne produisent pas de thèmes d'actualité et sont ignorées.

## 4. Data Model

### `KeywordLanguage.java` (enum — fichier propre)

```java
public enum KeywordLanguage {
    FR("french"),
    EN("english");

    private final String gdeltSourceLang;

    KeywordLanguage(String gdeltSourceLang) {
        this.gdeltSourceLang = gdeltSourceLang;
    }

    public String getGdeltSourceLang() {
        return gdeltSourceLang;
    }
}
```

L'enum porte directement la valeur utilisée par le paramètre `sourcelang` de l'API GDELT DOC 2.0. Point unique de vérité pour la correspondance langue/paramètre.

### `GdeltKeyword.java` (record)

```java
public record GdeltKeyword(
    String term,
    KeywordLanguage language
) {}
```

### `GdeltLeafThemes.java` (record)

```java
public record GdeltLeafThemes(
    String leafPath,
    String sourceHash,
    List<GdeltKeyword> keywords,
    Instant indexedAt
) {}
```

- `sourceHash` : hash SHA-256 tronqué de la valeur NL qui a produit ces keywords. Permet de détecter les désynchronisations au démarrage.
- `indexedAt` : timestamp de l'extraction, à titre informatif.

## 5. Composants

### Package `UserModel.GdeltThemeIndex`

| Classe | Rôle |
|--------|------|
| `KeywordLanguage` | Enum FR/EN avec mapping vers `sourcelang` GDELT |
| `GdeltKeyword` | Record (term, language) |
| `GdeltLeafThemes` | Record (leafPath, sourceHash, keywords, indexedAt) |
| `GdeltThemeIndexProperties` | `@ConfigurationProperties(prefix = "arcos.user-model.gdelt-theme-index")` |
| `GdeltThemeIndexRepository` | Persistence JSON : load/save du fichier `data/gdelt-theme-index.json` |
| `GdeltThemeExtractor` | Appel LLM local (Qwen3/Ollama) pour extraire des keywords depuis une valeur NL |
| `GdeltThemeIndexService` | Logique métier : `onLeafMutated()`, réconciliation au démarrage |
| `GdeltThemeIndexGate` | Facade publique : `getAllKeywords()`, `getKeywordsForPath()` |

### Conditionality

Tous les beans du package sont annotés `@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)`. Si GDELT est désactivé, aucun bean n'est créé.

Le `TreeOperationService` reçoit `GdeltThemeIndexService` via `@Nullable` constructor injection. Si le bean n'existe pas, l'appel de notification est simplement ignoré.

### Thread safety

L'index in-memory utilise un `ConcurrentHashMap<String, GdeltLeafThemes>`. Les mutations (batch pipeline thread) et les lectures (conversation thread via briefing) peuvent être concurrentes. `ConcurrentHashMap` offre des garanties suffisantes pour ce pattern à faible contention.

### Startup ordering

`GdeltThemeIndexService` constructor-injecte `PersonaTreeGate`. Cette dépendance transitoire (`GdeltThemeIndexService` → `PersonaTreeGate` → `PersonaTreeService` → `PersonaTreeSchemaLoader`) garantit que le `@PostConstruct` du PersonaTree est exécuté avant celui du GdeltThemeIndex. La réconciliation au démarrage peut donc lire les feuilles de l'arbre en toute sécurité.

## 6. Flux de données

### 6.1 Mutation d'une feuille pertinente (temps réel, dans le batch pipeline)

```
1. BatchPipelineOrchestrator traite un chunk de conversation
2. MemListenerClient produit : ADD(Life_Beliefs.Public_Interest_Engagement, "...")
3. TreeOperationService.applyOperation() :
   a. Applique l'opération sur le PersonaTree
   b. Vérifie : le path commence par un préfixe GDELT-pertinent ?
   c. OUI → appelle gdeltThemeIndexService.onLeafMutated(path, value, ADD)
4. GdeltThemeIndexService.onLeafMutated() :
   a. Si ADD/UPDATE : appelle GdeltThemeExtractor.extract(path, value)
      → LLM local génère des keywords (fr/en)
      → Stocke dans l'index avec hash(value)
      → Repository persiste le JSON
   b. Si DELETE : supprime l'entrée de l'index, persiste
```

### 6.2 Réconciliation au démarrage

```
1. GdeltThemeIndexService @PostConstruct :
   a. Repository charge gdelt-theme-index.json
   b. Pour chaque feuille GDELT-pertinente non-vide du PersonaTree :
      - Si absente de l'index → marquer pour extraction
      - Si présente mais sourceHash != hash(valeur actuelle) → marquer pour ré-extraction
   c. Pour chaque entrée de l'index sans feuille correspondante → supprimer
   d. Exécuter les extractions manquantes (appels LLM local)
   e. Persister
```

### 6.3 Consommation par le mode briefing

```
1. GdeltAnalysisService.generateBriefing() :
   a. Récupère les keywords via GdeltThemeIndexGate.getAllKeywords()
   b. Groupe/pondère les keywords
   c. Construit les requêtes GDELT DOC API
   d. Appelle l'API, compose le rapport
```

## 7. Prompt d'extraction LLM

Le `GdeltThemeExtractor` envoie au LLM local un prompt court et ciblé. Il reçoit seulement le path human-readable et la valeur NL, pas l'arbre complet.

### Template

```
Tu es un extracteur de mots-clés d'actualité.

Étant donné un trait de personnalité, génère des mots-clés de recherche
d'actualités pertinents pour cette personne.

Règles :
- Entre 2 et 5 mots-clés
- Chaque mot-clé est directement utilisable comme terme de recherche
- Alterne français et anglais pour maximiser la couverture
- Privilégie les termes concrets (noms propres, domaines) aux termes abstraits
- Termes de 1 à 4 mots maximum

Format de sortie (un par ligne) :
fr:terme en français
en:term in english

Catégorie : {humanReadablePath}
Valeur : {leafValue}

Mots-clés :
```

### Parsing

Regex : `^(fr|en):(.+)$`

Lignes hors-format → skip avec log warn. Réponse vide ou garbage → **l'entrée n'est pas persistée** (laissée absente de l'index). La prochaine réconciliation au démarrage détectera l'absence et retentera l'extraction. Ce comportement évite de stocker un état "vide" qui empêcherait le self-healing.

### Choix du modèle

Le MemListener est fine-tuné pour les opérations ADD/UPDATE/DELETE sur l'arbre. L'extraction de keywords est une tâche différente (NL → termes de recherche). **À valider empiriquement** : le modèle fine-tuné peut ne pas être optimal pour cette tâche. Option de fallback : utiliser le modèle base Qwen3 pour l'extraction.

### Client LLM

Le `GdeltThemeExtractor` **ne réutilise pas directement le `MemListenerClient`**. Ce dernier est hardcodé sur `UserModelProperties` (modèle, température, tokens). L'extracteur injecte `OllamaChatModel` et `GdeltThemeIndexProperties` pour construire ses propres appels avec une configuration indépendante. Le mode `ThinkingMode.NO_THINK` est réutilisé.

## 8. Persistence

### Fichier

`data/gdelt-theme-index.json`, même répertoire que `user-tree.json`.

### Format

```json
{
  "version": 1,
  "entries": {
    "4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement": {
      "sourceHash": "a1b2c3d4e5f6",
      "keywords": [
        {"term": "transition écologique", "language": "FR"},
        {"term": "European Green Deal", "language": "EN"},
        {"term": "politique environnementale", "language": "FR"}
      ],
      "indexedAt": "2026-03-20T14:30:00Z"
    }
  }
}
```

### Strategy de sauvegarde

Sauvegarde après chaque mutation (les mutations sont rares — quelques par conversation). Pas de debounce nécessaire.

## 9. Configuration

Nouvelles propriétés dans `GdeltThemeIndexProperties` (`UserModel.GdeltThemeIndex`), namespace `arcos.user-model.gdelt-theme-index` :

| Propriété | Type | Défaut | Description |
|-----------|------|--------|-------------|
| `arcos.user-model.gdelt-theme-index.path` | String | `data/gdelt-theme-index.json` | Chemin du fichier d'index |
| `arcos.user-model.gdelt-theme-index.extractor-model` | String | (même que memlistener) | Modèle Ollama pour l'extraction |
| `arcos.user-model.gdelt-theme-index.extractor-max-tokens` | int | 128 | Tokens max pour l'extraction |
| `arcos.user-model.gdelt-theme-index.max-keywords-per-leaf` | int | 5 | Nombre max de keywords par feuille |

**Pourquoi pas dans `GdeltProperties` ?** Les beans qui consomment ces propriétés sont dans `UserModel.GdeltThemeIndex`. Si on les mettait dans `GdeltProperties` (`Tools.GdeltTool`), le UserModel importerait un package Tools — violation directe du principe de direction des dépendances (§2). Le namespace `arcos.user-model.*` est cohérent avec les propriétés existantes du UserModel.

La propriété `arcos.gdelt.enabled` (dans `GdeltProperties`) reste dans Tools et sert uniquement de gate `@ConditionalOnProperty`. Les beans du UserModel la lisent via l'annotation, sans importer la classe `GdeltProperties`.

## 10. Risques et mitigations

| Risque | Impact | Mitigation |
|--------|--------|------------|
| LLM local produit des keywords de mauvaise qualité | Briefings non pertinents | Validation empirique + fallback sur modèle base. Keywords vides = feuille ignorée pour le briefing, pas de crash |
| Extraction LLM échoue (timeout, Ollama down) | Feuille sans keywords | Dégradation gracieuse : l'entrée n'est pas persistée. La réconciliation au prochain startup détecte l'absence et retente |
| Désynchronisation index/arbre après crash | Keywords obsolètes | Réconciliation au @PostConstruct via comparaison de hash |
| Surcharge du LLM local pendant le batch | Rallonge le batch pipeline | Les extractions sont rares (2-5 feuilles pertinentes par conversation) et courtes (128 tokens). Impact négligeable |
