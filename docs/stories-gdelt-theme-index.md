# Stories : GdeltThemeIndex

**Source** : [design-gdelt-theme-index.md](design-gdelt-theme-index.md)
**Date** : 2026-03-20
**Priorité** : MoSCoW — toutes MUST (infrastructure fondamentale pour le mode briefing)
**Post-review** : Corrections appliquées suite à review architecturale (config placement, startup ordering, thread safety, error handling, LLM client)

---

## Epic : GdeltThemeIndex — Source spécialisée du UserModel

**Valeur métier** : Permettre au mode briefing de `Rapport_Actualites` de générer des rapports d'actualité personnalisés en transformant automatiquement le profil NL de l'utilisateur en mots-clés de recherche GDELT.

---

## Ordre d'implémentation

```
S1 → S2 → S3 → S4 → S5 → S6 → S7
```

---

### S1 — Data model et configuration

**En tant que** développeur,
**je veux** le data model (records, enum) et les propriétés de configuration du GdeltThemeIndex,
**afin de** pouvoir construire les couches supérieures sur des types stables.

**Scope :**
- Créer le package `UserModel.GdeltThemeIndex`
- `KeywordLanguage.java` — enum avec mapping `gdeltSourceLang`
- `GdeltKeyword.java` — record (term, language)
- `GdeltLeafThemes.java` — record (leafPath, sourceHash, keywords, indexedAt)
- `GdeltThemeIndexProperties.java` — `@ConfigurationProperties(prefix = "arcos.user-model.gdelt-theme-index")` avec : `path`, `extractorModel`, `extractorMaxTokens`, `maxKeywordsPerLeaf`
- Enregistrer `GdeltThemeIndexProperties` dans `UserModelAutoConfiguration` via `@EnableConfigurationProperties`
- Définir la constante `GDELT_RELEVANT_PATH_PREFIXES` (Set<String> des 6 préfixes de branches pertinentes)

**Critères d'acceptation :**
- [ ] `KeywordLanguage.FR.getGdeltSourceLang()` retourne `"french"`
- [ ] `KeywordLanguage.EN.getGdeltSourceLang()` retourne `"english"`
- [ ] Les records sont sérialisables/désérialisables via Jackson (test round-trip)
- [ ] `GdeltThemeIndexProperties` a les valeurs par défaut correctes (path: `data/gdelt-theme-index.json`, maxTokens: 128, maxKeywords: 5)
- [ ] Les propriétés sont sous le namespace `arcos.user-model.gdelt-theme-index.*` (PAS dans `arcos.gdelt.*`)
- [ ] La liste des préfixes pertinents couvre les 6 branches définies dans le design (§3)

**Fichiers créés/modifiés :**
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/KeywordLanguage.java` (nouveau)
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltKeyword.java` (nouveau)
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltLeafThemes.java` (nouveau)
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltThemeIndexProperties.java` (nouveau)
- `src/main/java/org/arcos/UserModel/UserModelAutoConfiguration.java` (modifié — ajout @EnableConfigurationProperties)

---

### S2 — Repository (persistence JSON)

**En tant que** développeur,
**je veux** un repository qui charge et sauvegarde l'index GDELT depuis/vers un fichier JSON,
**afin de** persister les keywords extraits entre les redémarrages.

**Scope :**
- `GdeltThemeIndexRepository.java` — load(Path) → Map, save(Map, Path)
- Gestion du format JSON versionné (`{"version": 1, "entries": {...}}`)
- Création du fichier s'il n'existe pas (retourne map vide)
- Gestion des erreurs de parsing (log + retourne map vide, pas de crash)

**Critères d'acceptation :**
- [ ] `load()` sur un fichier inexistant retourne une map vide sans erreur
- [ ] `load()` puis `save()` round-trip préserve toutes les données
- [ ] `load()` sur un fichier corrompu log une erreur et retourne une map vide
- [ ] Le JSON sauvegardé contient le champ `version: 1`
- [ ] Les `Instant` sont sérialisés en ISO-8601

**Fichiers créés :**
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltThemeIndexRepository.java`
- `src/test/java/org/arcos/UnitTests/UserModel/GdeltThemeIndex/GdeltThemeIndexRepositoryTest.java`

---

### S3 — Extractor (appel LLM local)

**En tant que** développeur,
**je veux** un extracteur qui transforme une valeur NL de feuille en keywords GDELT via le LLM local,
**afin de** produire des termes de recherche actionnables depuis le profil utilisateur.

**Scope :**
- `GdeltThemeExtractor.java` — `extract(String leafPath, String leafValue) → List<GdeltKeyword>`
- Construction du prompt (template §7 du design)
- Utilise `UserContextFormatter.humanReadablePath()` pour le path human-readable (dépendance cross-package `DfsNavigator` → documentée)
- Injecte `OllamaChatModel` + `GdeltThemeIndexProperties` directement (NE PAS réutiliser `MemListenerClient` — celui-ci est hardcodé sur `UserModelProperties`)
- Utilise `ThinkingMode.NO_THINK` pour l'appel LLM
- Parsing de la réponse : regex `^(fr|en):(.+)$`
- Troncature à `max-keywords-per-leaf`
- Retourne liste vide en cas d'échec (pas d'exception)

**Critères d'acceptation :**
- [ ] Parsing correct de lignes `fr:transition écologique` et `en:climate policy`
- [ ] Les lignes hors-format sont ignorées avec un log warn
- [ ] Une réponse vide du LLM retourne `List.of()` sans exception
- [ ] Un timeout LLM retourne `List.of()` sans exception
- [ ] Le nombre de keywords est tronqué à `max-keywords-per-leaf`
- [ ] Le prompt inclut le path human-readable (pas le path technique)
- [ ] Le mode `NO_THINK` est utilisé pour l'appel LLM
- [ ] Le modèle et les max-tokens sont lus depuis `GdeltThemeIndexProperties` (pas `UserModelProperties`)

**Fichiers créés :**
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltThemeExtractor.java`
- `src/test/java/org/arcos/UnitTests/UserModel/GdeltThemeIndex/GdeltThemeExtractorTest.java`

---

### S4 — Service (logique métier + réconciliation)

**En tant que** développeur,
**je veux** un service qui maintient l'index à jour en réponse aux mutations de l'arbre et qui se réconcilie au démarrage,
**afin de** garantir la cohérence entre le PersonaTree et l'index GDELT.

**Scope :**
- `GdeltThemeIndexService.java`
- Constructor-injecte `PersonaTreeGate` (garantit l'ordre `@PostConstruct` par chaîne de dépendances)
- Index in-memory : `ConcurrentHashMap<String, GdeltLeafThemes>` (thread safety pour lectures concurrentes pendant briefing)
- `onLeafMutated(String path, String value, TreeOperationType type)` :
  - ADD/UPDATE → extrait keywords via extractor ; si succès, stocke avec hash et persiste ; si échec, ne persiste pas (laisse absent pour retry au prochain startup)
  - DELETE → supprime l'entrée, persiste
- `@PostConstruct reconcile()` :
  - Repository charge le fichier JSON
  - Compare les feuilles pertinentes non-vides du PersonaTree (via `PersonaTreeGate`) avec l'index
  - Ré-extrait les entrées stale (hash mismatch) ou manquantes
  - Supprime les entrées orphelines
  - Persiste
  - Si l'index est déjà en sync : aucun appel LLM, aucune persistence (idempotent)
- Méthode utilitaire `isGdeltRelevantPath(String path)` — vérifie contre la liste de préfixes
- Méthode utilitaire `hashValue(String value)` — SHA-256 tronqué
- `@ConditionalOnProperty(name = "arcos.gdelt.enabled")`

**Critères d'acceptation :**
- [ ] `onLeafMutated(path, value, ADD)` crée une entrée dans l'index avec le hash correct
- [ ] `onLeafMutated(path, null, DELETE)` supprime l'entrée de l'index
- [ ] `onLeafMutated()` sur un path non-pertinent est un no-op
- [ ] Si l'extraction LLM échoue sur un ADD, l'entrée n'est PAS persistée (reste absente de l'index)
- [ ] Au démarrage, les entrées avec hash mismatch sont ré-extraites
- [ ] Au démarrage, les entrées orphelines (feuille vide/supprimée) sont supprimées
- [ ] Au démarrage, les feuilles pertinentes sans entrée sont extraites
- [ ] Au démarrage avec un index déjà en sync : 0 appels LLM, 0 écritures disque (idempotent)
- [ ] `isGdeltRelevantPath("1_Biological_Characteristics.Physical_Appearance.Hair")` retourne false
- [ ] `isGdeltRelevantPath("4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement")` retourne true
- [ ] Le constructor injecte `PersonaTreeGate` (garantie d'ordre @PostConstruct)

**Fichiers créés :**
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltThemeIndexService.java`
- `src/test/java/org/arcos/UnitTests/UserModel/GdeltThemeIndex/GdeltThemeIndexServiceTest.java`

---

### S5 — Gate (facade publique)

**En tant que** développeur,
**je veux** une facade publique pour le GdeltThemeIndex,
**afin que** les consommateurs externes (GdeltAnalysisService) puissent lire les keywords sans accéder aux internals.

**Scope :**
- `GdeltThemeIndexGate.java` — facade read-only
- `getAllKeywords() → List<GdeltKeyword>` — tous les keywords agrégés, dédupliqués
- `getKeywordsForPath(String leafPath) → List<GdeltKeyword>` — keywords d'une feuille
- `getIndexedLeafCount() → int` — nombre de feuilles indexées (diagnostic)
- `isEmpty() → boolean`
- `@ConditionalOnProperty(name = "arcos.gdelt.enabled")`

**Critères d'acceptation :**
- [ ] `getAllKeywords()` retourne une liste dédupliquée (pas de keyword identique)
- [ ] `getAllKeywords()` sur un index vide retourne `List.of()`
- [ ] `getKeywordsForPath()` retourne `List.of()` pour un path non-indexé
- [ ] La gate ne expose aucune méthode d'écriture

**Fichiers créés :**
- `src/main/java/org/arcos/UserModel/GdeltThemeIndex/GdeltThemeIndexGate.java`
- `src/test/java/org/arcos/UnitTests/UserModel/GdeltThemeIndex/GdeltThemeIndexGateTest.java`

---

### S6 — Intégration dans TreeOperationService

**En tant que** développeur,
**je veux** que le TreeOperationService notifie le GdeltThemeIndexService lors des mutations de feuilles pertinentes,
**afin que** l'index soit mis à jour en temps réel lors du batch pipeline.

**Scope :**
- Modifier `TreeOperationService` : ajouter `@Nullable GdeltThemeIndexService` en constructor injection
- Dans `applyOperation()` : après une opération réussie (ADD/UPDATE/DELETE) sur un path pertinent, appeler `gdeltThemeIndexService.onLeafMutated()`
- Si le bean est null (GDELT désactivé), ne rien faire
- Wrap l'appel dans try/catch pour que l'échec de l'indexation ne fasse pas échouer l'opération sur l'arbre

**Critères d'acceptation :**
- [ ] Avec GDELT activé : un ADD réussi sur une feuille pertinente déclenche `onLeafMutated()`
- [ ] Avec GDELT activé : un ADD réussi sur une feuille NON-pertinente ne déclenche rien
- [ ] Avec GDELT désactivé (`gdeltThemeIndexService == null`) : aucune erreur, le flux existant est inchangé
- [ ] Une opération en échec (path invalide) ne déclenche pas `onLeafMutated()`
- [ ] Si `onLeafMutated()` lève une exception, l'opération sur l'arbre reste réussie (pas de rollback)
- [ ] Les tests existants de `TreeOperationService` passent toujours (non-régression)

**Fichiers modifiés :**
- `src/main/java/org/arcos/UserModel/PersonaTree/TreeOperationService.java`
- `src/test/java/org/arcos/UnitTests/UserModel/PersonaTree/TreeOperationServiceTest.java`

---

### S7 — Connexion GdeltAnalysisService → GdeltThemeIndexGate

**En tant que** développeur,
**je veux** que le GdeltAnalysisService utilise la gate pour récupérer les keywords en mode briefing,
**afin de** remplacer le stub actuel par un vrai accès aux intérêts utilisateur indexés.

**Scope :**
- Modifier `GdeltAnalysisService.generateBriefing()` : injecter `GdeltThemeIndexGate`
- Supprimer la dépendance directe vers `PersonaTreeService` (remplacée par la gate)
- Remplacer le stub par un appel à `gate.getAllKeywords()`
- Retourner un message informatif listant les keywords trouvés (l'appel DOC API vient dans un sprint ultérieur)
- Gérer le cas index vide (message explicite)

**Critères d'acceptation :**
- [ ] `generateBriefing()` avec un index non-vide retourne un message contenant les keywords
- [ ] `generateBriefing()` avec un index vide retourne un message explicatif
- [ ] `PersonaTreeService` n'est plus injecté directement dans `GdeltAnalysisService`
- [ ] Le `@ConditionalOnBean` existant sur `GdeltActions` continue de fonctionner
- [ ] Le test existant `GdeltAnalysisService` est mis à jour

**Fichiers modifiés :**
- `src/main/java/org/arcos/Tools/GdeltTool/GdeltAnalysisService.java`

---

## Résumé

| Story | Scope | Fichiers nouveaux | Fichiers modifiés |
|-------|-------|-------------------|-------------------|
| S1 | Data model + config | 4 | 1 |
| S2 | Repository JSON | 1 + 1 test | 0 |
| S3 | Extractor LLM | 1 + 1 test | 0 |
| S4 | Service + réconciliation | 1 + 1 test | 0 |
| S5 | Gate facade | 1 + 1 test | 0 |
| S6 | Hook TreeOperationService | 0 | 2 |
| S7 | Connexion GdeltAnalysisService | 0 | 1 |
| **Total** | | **8 + 4 tests** | **4** |
