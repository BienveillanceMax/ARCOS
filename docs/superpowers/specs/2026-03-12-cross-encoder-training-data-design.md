# Cross-Encoder Training Data Pipeline — Design Spec

**Date**: 2026-03-12
**Project**: ARCOS / PersonaTree Navigator
**Location**: `/home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator/`

## Context

ARCOS uses a cross-encoder (~22M params) to navigate the PersonaTree (user profile based on
Biopsychosocial model, ~480 leaves, 20 L2 branches) logarithmically at each conversation turn.
The model scores (user_utterance, branch_description) pairs for relevance.

**Current state**: 1,020 pairs, single-label, random negatives, English base model (ms-marco-MiniLM-L-6-v2).
Post-training: 73.9% accuracy, 0.752 AP. Poor on cross-domain associations and frequent false positives.

**Goal**: Build a high-quality multi-label French training dataset of ~20,000-25,000 pairs to
significantly improve the model's accuracy, especially on hard/ambiguous cases.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Label strategy | Multi-label | A query can be relevant to 2-4 branches simultaneously; avoids false negatives |
| Negative ratio | 50/50 hard/easy | Sweet spot — forces discrimination without over-conservatism |
| Query register | Familiar/oral French | Matches real voice assistant usage |
| Query types | Mix of 4 styles | (A) everyday, (B) indirect, (C) short/fragmented, (D) questions to assistant |
| Data sources | Hybrid: real datasets + Claude generation | Real data for linguistic diversity, Claude for coverage + labeling |
| Target volume | ~3,500-4,500 unique queries → ~20,000-25,000 training pairs | Sufficient for 22M param model |

## Branch Taxonomy (20 L2 branches)

The 20 branches with their French descriptions (used as cross-encoder input):

1. **Physical_Appearance** — Apparence physique : morphologie, traits du visage, peau, cheveux, voix
2. **Physiological_Status** — Etat physiologique : sante, forme physique, fonctions sensorielles, age, parametres vitaux
3. **Biological_Rhythms** — Rythmes biologiques : rythme circadien, cycles d'energie, sommeil, rythmes alimentaires
4. **Cognitive_Abilities** — Capacites cognitives : intelligence, memoire, attention, apprentissage, creativite, perception
5. **Thinking_Patterns** — Schemas de pensee : style de decision, resolution de problemes, tendances cognitives, flexibilite mentale
6. **Psychological_State** — Etat psychologique : bien-etre, sante mentale, niveau de stress, humeur de base, anxiete
7. **Self_Perception** — Perception de soi : estime de soi, confiance, efficacite personnelle, identite
8. **Core_Personality** — Personnalite fondamentale : ouverture, conscienciosite, extraversion, agreabilite, nevrosisme, MBTI
9. **Emotional_Characteristics** — Caracteristiques emotionnelles : spectre emotionnel, expression, regulation, empathie, humour, sensibilite
10. **Coping_with_Adversity** — Gestion de l'adversite : mecanismes d'adaptation, resilience, recuperation, mentalite de croissance
11. **Life_Beliefs** — Croyances : valeurs, vision du monde, principes moraux, foi, position politique, sens de la vie
12. **Motivations_and_Goals** — Motivations et objectifs : forces motrices, aspirations, objectifs a long et court terme
13. **Social_Identity** — Identite sociale : culture, statut socioeconomique, education, metier, lieu de residence
14. **Family_System** — Systeme familial : statut marital, roles familiaux, relations, responsabilites, animaux
15. **Life_Course** — Parcours de vie : recit personnel, evenements cles, etape de vie, orientation future
16. **Behavioral_Habits** — Habitudes comportementales : routine, alimentation, vetements, exercice, hygiene, sommeil
17. **Lifestyle** — Mode de vie : emploi du temps, loisirs, sante emotionnelle, bien-etre, logement, voyages
18. **Social_Interactions** — Interactions sociales : style de communication, preferences sociales, relations, gestion des conflits
19. **Interests_and_Skills** — Interets et competences : loisirs, competences professionnelles, connaissances, creations
20. **Resource_Management** — Gestion des ressources : temps, finances, espace, energie, organisation

## Pipeline Overview

```
Phase 1: Mine FR datasets    → ~1,500-2,500 raw utterances
Phase 2: Claude generation   → ~1,500-2,500 additional queries
Phase 3: Claude multi-label  → label all queries against 20 branches (0/1)
Phase 4: Pair construction   → expand to ~20,000-25,000 pairs (pos + hard/easy neg)
Phase 5: Human validation    → user reviews ~200 sample pairs
```

## Phase 1 — Mining French Conversational Datasets

### Target Datasets

| Dataset | Source | Why |
|---------|--------|-----|
| wraps/everyday-conversations-llama3.1-2k-french | HuggingFace | FR user turns from everyday conversations (shopping, work, cooking, travel). |
| cfierro/personality-qs-french | HuggingFace | FR self-descriptions and personality responses. Relevant for identity/personality branches. |
| FrancophonIA/french-conversational-dataset | HuggingFace | Varied FR conversational turns with context. |

Note: Originally planned datasets (Claire-Dialogue-French, CATIE-AQ, OpenSubtitles) are either
gated, unavailable, or use deprecated HuggingFace script format. The above are accessible alternatives.

### Extraction Pipeline

**Script**: `mine_datasets.py`

1. Download/stream each dataset via HuggingFace `datasets` library
2. Extract individual turns of speech (1 utterance per candidate)
3. Filter criteria:
   - Length: 4-50 words
   - First person: contains je/j'/mon/ma/mes/moi/me/m' OR is a personal question
   - Not a question about the interlocutor (tu/vous/ton/ta/tes)
   - Not meta-conversational ("bonjour", "au revoir", "d'accord")
4. Deduplicate: exact match + fuzzy matching (Levenshtein ratio > 0.85)
5. Output: `mined_utterances.jsonl` — one utterance per line with source metadata

**Target**: ~1,500-2,500 usable utterances.

## Phase 2 — Claude Generation

### Strategy

After Phase 1, analyze branch coverage gaps. For each under-represented branch, generate
queries using Claude with the following prompt structure:

**4 query styles per branch:**
- **(A) Vie courante** — Natural everyday statements ("Putain j'en ai marre de ce boulot")
- **(B) Indirect** — Never names the topic directly ("Mon fils m'a encore appele hier" → Family)
- **(C) Court/fragmente** — 1-5 words, oral fragments ("Fatigue.", "Encore en retard...")
- **(D) Question a l'assistant** — Directed at the AI ("Tu penses quoi de moi ?", "Rappelle-moi ce que j'aime bien")

**Generation prompt template:**
```
Generate N unique French utterances that a user might say to their home voice assistant.
These utterances should be relevant to the branch: [branch_description].

Style: [A/B/C/D description]
Register: familiar/oral French (tu, contractions, slang, fragments OK)
Constraints:
- Each utterance must be 1-50 words
- Vary vocabulary, don't repeat patterns
- Include hesitations, fillers ("euh", "bah", "genre")
- Some utterances should be ambiguous (could touch multiple branches)
```

**Script**: `generate_queries.py`

**Deduplication**: after generation, deduplicate against Phase 1 utterances using
exact match + fuzzy matching (Levenshtein ratio > 0.85) to avoid overlap.

**Target**: ~1,500-2,500 generated queries to complement mined data.

## Phase 3 — Multi-Label Annotation by Claude

### Strategy

All queries (mined + generated) are annotated against all 20 branches by Claude.

**Annotation prompt:**
```
Given the following user utterance spoken to a home voice assistant, determine which
PersonaTree branches are relevant. A branch is relevant if the utterance reveals information
about, asks about, or relates to that aspect of the user's profile.

Utterance: "[query]"

For each branch, output 1 (relevant) or 0 (not relevant):
[list of 20 branches with descriptions]

Output as JSON: {"Physical_Appearance": 0, "Physiological_Status": 1, ...}
```

**Batching**: Process in batches of 10-20 queries per Claude call. Use structured JSON
output. On malformed output, retry the batch (max 2 retries) then flag for manual review.

**Script**: `label_queries.py`

**Output**: `labeled_queries.jsonl` — each line: `{"query": "...", "labels": {"branch": 0|1, ...}, "source": "mined|generated"}`

## Phase 4 — Training Pair Construction

### Strategy

For each query, construct training pairs:

1. **All positive pairs**: query × each branch where label=1 (~2-3 per query avg)
2. **Negative sampling**: for each query, sample N negatives where N = number of positives
   (1:1 pos/neg ratio per query). Split 50/50 hard/easy:
   - **Hard negatives**: among branches with label=0, pick the top-K closest to any
     positive branch (by branch similarity rank). "Closest" = highest cosine similarity
     to any of the query's positive branches.
   - **Easy negatives**: randomly sampled from the remaining label=0 branches.
3. **Queries with 0 positive branches**: discard (not useful for training).

With ~4,000 queries, avg 2.5 positives per query, and 1:1 neg sampling:
~4,000 × (2.5 pos + 2.5 neg) = ~20,000 pairs.

### Branch Similarity Matrix

Pre-compute cosine similarity between all 20 branch descriptions using a sentence-transformer.
For hard negative selection, rank label=0 branches by similarity to the query's positive
branches and pick from the top of that ranking (no fixed threshold).

**Script**: `build_training_pairs.py`

**Output**: `training_pairs.jsonl` — each line:
```json
{"query": "...", "branch_description": "...", "branch_key": "...", "label": 1, "pair_type": "positive|hard_neg|easy_neg", "source": "mined|generated"}
```

**Train/test split**: query-level split (all pairs from one query go to the same split)
to prevent data leakage. Ratio: 85% train / 15% eval. Split is deterministic (seed=42).

**Target**: ~20,000-25,000 pairs total.

## Phase 5 — Human Validation

Sample ~200 queries (not pairs) and present their full label vectors (20 branches)
for human review. Stratify by source (mined vs generated).

Measure human-vs-Claude agreement on these labels. If agreement < 85%, investigate
systematic errors and re-run Phase 3 with adjusted prompts.

**Script**: `sample_for_review.py` — outputs a TSV file for easy review.

## Output Files

All outputs go in `benchmarks/tree-navigator/data/`:

```
data/
  mined_utterances.jsonl       — Phase 1 output
  generated_queries.jsonl      — Phase 2 output
  labeled_queries.jsonl        — Phase 3 output
  branch_similarity.json       — Branch similarity matrix
  training_pairs.jsonl         — Phase 4 output (final dataset)
  review_sample.tsv            — Phase 5 validation sample
```

## Success Criteria

- 20,000-25,000 training pairs
- Multi-label coverage: avg 2-3 positive branches per query
- 50/50 hard/easy negative ratio
- Human validation agreement >= 85%
- Post-training model accuracy improvement over current 73.9% baseline
