# Cross-Encoder Training Data Pipeline — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a ~20,000-25,000 pair multi-label French training dataset for the PersonaTree navigator cross-encoder.

**Architecture:** 5-phase pipeline — mine HuggingFace datasets for real French utterances, generate additional queries via Claude API, annotate all queries multi-label via Claude, construct balanced training pairs with hard/easy negatives, then sample for human validation. Each phase is a standalone Python script with JSONL I/O.

**Tech Stack:** Python 3.12, HuggingFace `datasets`, `anthropic` SDK, `sentence-transformers`, `rapidfuzz`, `numpy`

**Spec:** `docs/superpowers/specs/2026-03-12-cross-encoder-training-data-design.md`

---

## File Structure

```
benchmarks/tree-navigator/
  shared.py                      — Branch taxonomy, paths, constants (CREATE)
  mine_datasets.py               — Phase 1: mine HF datasets (CREATE)
  generate_queries.py            — Phase 2: Claude generation (CREATE)
  label_queries.py               — Phase 3: Claude multi-label annotation (CREATE)
  build_training_pairs.py        — Phase 4: pair construction + train/eval split (CREATE)
  sample_for_review.py           — Phase 5: validation sample extraction (CREATE)
  data/                          — All output files (CREATE dir)
    mined_utterances.jsonl
    generated_queries.jsonl
    all_queries.jsonl
    all_queries.jsonl
    labeled_queries.jsonl
    branch_similarity.json
    training_pairs.jsonl
    training_pairs_train.jsonl
    training_pairs_eval.jsonl
    review_sample.tsv
```

---

## Chunk 1: Setup + Shared Constants

### Task 1: Install missing dependencies

**Files:**
- Modify: `benchmarks/tree-navigator/.venv/` (via uv/pip)

- [ ] **Step 1: Install anthropic, rapidfuzz**

```bash
cd /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator
.venv/bin/pip install anthropic rapidfuzz
```

Expected: both packages install successfully.

- [ ] **Step 2: Verify imports**

```bash
.venv/bin/python -c "import anthropic; import rapidfuzz; print('OK')"
```

Expected: `OK`

### Task 2: Create shared constants module

**Files:**
- Create: `benchmarks/tree-navigator/shared.py`
- Create: `benchmarks/tree-navigator/data/` (directory)

- [ ] **Step 1: Create data output directory**

```bash
mkdir -p /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator/data
```

- [ ] **Step 2: Write shared.py with branch taxonomy and paths**

```python
#!/usr/bin/env python3
"""Shared constants for the training data pipeline."""

from pathlib import Path

BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data"

# 20 L2 branches with their French descriptions (cross-encoder input)
BRANCHES = {
    "Physical_Appearance": "Apparence physique : morphologie, traits du visage, peau, cheveux, voix",
    "Physiological_Status": "État physiologique : santé, forme physique, fonctions sensorielles, âge, paramètres vitaux",
    "Biological_Rhythms": "Rythmes biologiques : rythme circadien, cycles d'énergie, sommeil, rythmes alimentaires",
    "Cognitive_Abilities": "Capacités cognitives : intelligence, mémoire, attention, apprentissage, créativité, perception",
    "Thinking_Patterns": "Schémas de pensée : style de décision, résolution de problèmes, tendances cognitives, flexibilité mentale",
    "Psychological_State": "État psychologique : bien-être, santé mentale, niveau de stress, humeur de base, anxiété",
    "Self_Perception": "Perception de soi : estime de soi, confiance, efficacité personnelle, identité",
    "Core_Personality": "Personnalité fondamentale : ouverture, conscienciosité, extraversion, agréabilité, névrosisme, MBTI",
    "Emotional_Characteristics": "Caractéristiques émotionnelles : spectre émotionnel, expression, régulation, empathie, humour, sensibilité",
    "Coping_with_Adversity": "Gestion de l'adversité : mécanismes d'adaptation, résilience, récupération, mentalité de croissance",
    "Life_Beliefs": "Croyances : valeurs, vision du monde, principes moraux, foi, position politique, sens de la vie",
    "Motivations_and_Goals": "Motivations et objectifs : forces motrices, aspirations, objectifs à long et court terme",
    "Social_Identity": "Identité sociale : culture, statut socioéconomique, éducation, métier, lieu de résidence",
    "Family_System": "Système familial : statut marital, rôles familiaux, relations, responsabilités, animaux",
    "Life_Course": "Parcours de vie : récit personnel, événements clés, étape de vie, orientation future",
    "Behavioral_Habits": "Habitudes comportementales : routine, alimentation, vêtements, exercice, hygiène, sommeil",
    "Lifestyle": "Mode de vie : emploi du temps, loisirs, santé émotionnelle, bien-être, logement, voyages",
    "Social_Interactions": "Interactions sociales : style de communication, préférences sociales, relations, gestion des conflits",
    "Interests_and_Skills": "Intérêts et compétences : loisirs, compétences professionnelles, connaissances, créations",
    "Resource_Management": "Gestion des ressources : temps, finances, espace, énergie, organisation",
}

BRANCH_KEYS = list(BRANCHES.keys())

# Filtering constants for Phase 1
FIRST_PERSON_MARKERS = {
    "je ", "j'", "j'", "mon ", "ma ", "mes ", "moi ", "moi,", "moi.",
    "me ", "m'", "m'",
}
META_PHRASES = {
    "bonjour", "bonsoir", "salut", "au revoir", "merci", "d'accord",
    "ok", "oui", "non", "ouais", "bien sûr", "exactement", "voilà",
    "je vois", "ah bon", "c'est ça", "entendu",
}
MIN_WORDS = 4
MAX_WORDS = 50
```

- [ ] **Step 3: Verify shared.py loads correctly**

```bash
.venv/bin/python -c "from shared import BRANCHES, DATA_DIR; print(f'{len(BRANCHES)} branches, data_dir={DATA_DIR}')"
```

Expected: `20 branches, data_dir=.../tree-navigator/data`

- [ ] **Step 4: Commit**

```bash
git add benchmarks/tree-navigator/shared.py benchmarks/tree-navigator/data/.gitkeep
git commit -m "feat(tree-nav): shared constants for training data pipeline"
```

---

## Chunk 2: Phase 1 — Mine HuggingFace Datasets

### Task 3: Create mine_datasets.py

**Files:**
- Create: `benchmarks/tree-navigator/mine_datasets.py`

This script mines 3 HuggingFace datasets for French first-person utterances:
1. `wraps/everyday-conversations-llama3.1-2k-french` — user turns from FR conversations
2. `cfierro/personality-qs-french` — French personality self-descriptions (assistant responses are FR self-descriptions)
3. `FrancophonIA/french-conversational-dataset` — varied FR conversational turns

- [ ] **Step 1: Write mine_datasets.py**

The script should:
1. Stream each dataset from HuggingFace
2. Extract candidate utterances:
   - For `wraps/everyday-conversations`: extract user-role messages from `messages` field (parse JSON if string)
   - For `cfierro/personality-qs-french`: extract assistant-role messages (they're FR self-descriptions)
   - For `FrancophonIA/french-conversational-dataset`: extract `response` field + items from `context` list
3. Apply filters from `shared.py`:
   - Length: MIN_WORDS to MAX_WORDS
   - First person: at least one marker from FIRST_PERSON_MARKERS (case-insensitive) in the utterance
   - Not meta-conversational: first 3 words lowered not in META_PHRASES
   - Not a question about the interlocutor: skip if starts with "tu ", "vous ", "ton ", "ta ", "tes ", "votre ", "vos "
4. Deduplicate:
   - Exact match on lowered+stripped text
   - Fuzzy: `rapidfuzz.fuzz.ratio > 85` against all kept utterances (use a list and check each new candidate against it — slow but fine for <10k candidates)
5. Write `data/mined_utterances.jsonl` — one JSON per line: `{"query": "...", "source": "dataset_name"}`
6. Print stats: total candidates per dataset, after filtering, after dedup

Key implementation details:
- Use `streaming=True` to avoid downloading full datasets
- Cap each dataset at 5000 examples streamed (avoid infinite iteration)
- For fuzzy dedup, use `rapidfuzz.fuzz.ratio` which is fast in C
- The personality dataset responses are formal — still include them but they'll be a minority

```python
#!/usr/bin/env python3
"""Phase 1: Mine HuggingFace datasets for French first-person utterances."""

import json
from datasets import load_dataset
from rapidfuzz import fuzz
from shared import (
    DATA_DIR, FIRST_PERSON_MARKERS, META_PHRASES, MIN_WORDS, MAX_WORDS,
)


def is_first_person(text: str) -> bool:
    lower = text.lower()
    return any(m in lower for m in FIRST_PERSON_MARKERS)


def is_meta(text: str) -> bool:
    lower = text.lower().strip()
    return lower in META_PHRASES or any(lower.startswith(p) for p in META_PHRASES)


def is_about_interlocutor(text: str) -> bool:
    prefixes = ("tu ", "vous ", "ton ", "ta ", "tes ", "votre ", "vos ")
    return text.lower().strip().startswith(prefixes)


def passes_filters(text: str) -> bool:
    text = text.strip()
    if not text:
        return False
    words = text.split()
    if len(words) < MIN_WORDS or len(words) > MAX_WORDS:
        return False
    if not is_first_person(text):
        return False
    if is_meta(text):
        return False
    if is_about_interlocutor(text):
        return False
    return True


def deduplicate(utterances: list[dict], threshold: float = 85.0) -> list[dict]:
    kept = []
    kept_texts = []
    for item in utterances:
        text = item["query"].lower().strip()
        if text in kept_texts:
            continue
        is_dup = False
        for existing in kept_texts:
            if fuzz.ratio(text, existing) > threshold:
                is_dup = True
                break
        if not is_dup:
            kept.append(item)
            kept_texts.append(text)
    return kept


def mine_wraps() -> list[dict]:
    """Mine wraps/everyday-conversations-llama3.1-2k-french."""
    print("Mining wraps/everyday-conversations-llama3.1-2k-french...")
    ds = load_dataset(
        "wraps/everyday-conversations-llama3.1-2k-french",
        split="train", streaming=True,
    )
    candidates = []
    for i, ex in enumerate(ds):
        if i >= 5000:
            break
        msgs = ex["messages"]
        if isinstance(msgs, str):
            msgs = json.loads(msgs)
        for msg in msgs:
            if isinstance(msg, str):
                msg = json.loads(msg)
            if msg.get("role") == "user":
                text = msg["content"].strip()
                if passes_filters(text):
                    candidates.append({
                        "query": text,
                        "source": "wraps/everyday-conversations",
                    })
    print(f"  → {len(candidates)} candidates after filtering")
    return candidates


def mine_personality() -> list[dict]:
    """Mine cfierro/personality-qs-french (assistant responses = FR self-descriptions)."""
    print("Mining cfierro/personality-qs-french...")
    ds = load_dataset(
        "cfierro/personality-qs-french",
        split="train", streaming=True,
    )
    candidates = []
    for i, ex in enumerate(ds):
        if i >= 5000:
            break
        msgs = ex["messages"]
        if isinstance(msgs, str):
            msgs = json.loads(msgs)
        for msg in msgs:
            if isinstance(msg, str):
                msg = json.loads(msg)
            if msg.get("role") == "assistant":
                text = msg["content"].strip()
                if passes_filters(text):
                    candidates.append({
                        "query": text,
                        "source": "cfierro/personality-qs-french",
                    })
    print(f"  → {len(candidates)} candidates after filtering")
    return candidates


def mine_francophonia() -> list[dict]:
    """Mine FrancophonIA/french-conversational-dataset."""
    print("Mining FrancophonIA/french-conversational-dataset...")
    ds = load_dataset(
        "FrancophonIA/french-conversational-dataset",
        split="train", streaming=True,
    )
    candidates = []
    for i, ex in enumerate(ds):
        if i >= 5000:
            break
        # Extract response
        resp = ex.get("response", "").strip()
        if passes_filters(resp):
            candidates.append({
                "query": resp,
                "source": "FrancophonIA/french-conversational",
            })
        # Extract context turns
        ctx = ex.get("context", [])
        if isinstance(ctx, list):
            for turn in ctx:
                if isinstance(turn, str) and passes_filters(turn):
                    candidates.append({
                        "query": turn.strip(),
                        "source": "FrancophonIA/french-conversational",
                    })
    print(f"  → {len(candidates)} candidates after filtering")
    return candidates


def main():
    all_candidates = []
    for miner in [mine_wraps, mine_personality, mine_francophonia]:
        all_candidates.extend(miner())

    print(f"\nTotal before dedup: {len(all_candidates)}")
    deduped = deduplicate(all_candidates)
    print(f"After dedup: {len(deduped)}")

    output_path = DATA_DIR / "mined_utterances.jsonl"
    with open(output_path, "w") as f:
        for item in deduped:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"Saved to {output_path}")

    # Stats by source
    from collections import Counter
    sources = Counter(item["source"] for item in deduped)
    for src, count in sources.most_common():
        print(f"  {src}: {count}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run mine_datasets.py**

```bash
cd /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator
.venv/bin/python mine_datasets.py
```

Expected: outputs `data/mined_utterances.jsonl` with stats printed. Target: 500-2500 utterances.
If fewer than 500, that's fine — Phase 2 will compensate.

- [ ] **Step 3: Inspect output quality**

```bash
head -20 data/mined_utterances.jsonl
wc -l data/mined_utterances.jsonl
```

Visually verify: utterances are French, first-person, not meta-conversational.

- [ ] **Step 4: Commit**

```bash
git add benchmarks/tree-navigator/mine_datasets.py
git commit -m "feat(tree-nav): Phase 1 — mine HuggingFace datasets for FR utterances"
```

---

## Chunk 3: Phase 2 — Claude Generation

### Task 4: Create generate_queries.py

**Files:**
- Create: `benchmarks/tree-navigator/generate_queries.py`

This script uses Claude API to generate diverse French utterances for each branch.
It reads `data/mined_utterances.jsonl` (Phase 1 output) to know how many more queries
are needed, then generates to reach a target of ~4000 total unique queries.

- [ ] **Step 1: Write generate_queries.py**

Key design:
- Read mined utterances count to calculate how many to generate
- Target: 4000 total queries (mined + generated)
- For each of the 20 branches, generate queries in 4 styles (A/B/C/D)
- Batch: ask Claude for 25-30 utterances per call (1 branch × 1 style per call)
- Total calls: 20 branches × 4 styles = 80 API calls
- After generation, deduplicate against Phase 1 output using rapidfuzz
- Output: `data/generated_queries.jsonl`

The script needs `ANTHROPIC_API_KEY` env var.

```python
#!/usr/bin/env python3
"""Phase 2: Generate diverse French utterances via Claude API."""

import json
import os
import time
from pathlib import Path

import anthropic
from rapidfuzz import fuzz
from shared import BRANCHES, BRANCH_KEYS, DATA_DIR

TARGET_TOTAL = 4000
QUERIES_PER_CALL = 30
MODEL = "claude-sonnet-4-20250514"

STYLES = {
    "A": {
        "name": "Vie courante / familier",
        "instruction": (
            "Phrases naturelles du quotidien, registre familier/oral. "
            "Contractions, argot, jurons légers OK. "
            'Ex: "Putain j\'en ai marre de ce boulot", "Bah j\'sais pas trop"'
        ),
    },
    "B": {
        "name": "Indirect",
        "instruction": (
            "Phrases qui NE NOMMENT JAMAIS directement le sujet de la branche. "
            "L'utilisateur parle d'autre chose mais ça révèle indirectement des infos sur cette branche. "
            'Ex pour Family: "Mon fils m\'a encore appelé hier" (jamais le mot "famille")'
        ),
    },
    "C": {
        "name": "Court / fragmenté",
        "instruction": (
            "Phrases très courtes (1-8 mots), fragments oraux, soupirs, exclamations. "
            'Ex: "Fatigué.", "Encore en retard...", "Pfff", "J\'en peux plus"'
        ),
    },
    "D": {
        "name": "Question à l'assistant",
        "instruction": (
            "Questions que l'utilisateur pose directement à son assistant vocal IA. "
            "Tutoiement, ton familier. "
            'Ex: "Tu penses quoi de moi ?", "Rappelle-moi ce que j\'aime bien", '
            '"C\'est quoi déjà mon truc préféré ?"'
        ),
    },
}


def load_existing_queries() -> list[str]:
    """Load mined utterances to avoid duplicates."""
    path = DATA_DIR / "mined_utterances.jsonl"
    queries = []
    if path.exists():
        with open(path) as f:
            for line in f:
                item = json.loads(line)
                queries.append(item["query"])
    return queries


def is_duplicate(text: str, existing: list[str], threshold: float = 85.0) -> bool:
    lower = text.lower().strip()
    for ex in existing:
        if fuzz.ratio(lower, ex.lower().strip()) > threshold:
            return True
    return False


def generate_batch(
    client: anthropic.Anthropic,
    branch_key: str,
    branch_desc: str,
    style_key: str,
    style: dict,
    n: int = QUERIES_PER_CALL,
) -> list[str]:
    """Generate N utterances for one branch + style combination."""
    prompt = f"""Génère exactement {n} phrases uniques en français qu'un utilisateur pourrait dire à son assistant vocal domestique.

Ces phrases doivent être pertinentes pour la branche du profil utilisateur :
**{branch_key}** — {branch_desc}

Style demandé : **{style['name']}**
{style['instruction']}

Contraintes :
- Registre familier/oral (comme une vraie conversation orale)
- Varie le vocabulaire, ne répète pas les mêmes structures
- Inclus des hésitations, fillers ("euh", "bah", "genre", "en fait") dans certaines phrases
- Certaines phrases peuvent être ambiguës (toucher plusieurs branches)
- Longueur : 1 à 50 mots par phrase
- PAS de numérotation, juste une phrase par ligne

Réponds UNIQUEMENT avec les {n} phrases, une par ligne, sans numéro ni tiret."""

    response = client.messages.create(
        model=MODEL,
        max_tokens=4096,
        messages=[{"role": "user", "content": prompt}],
    )

    text = response.content[0].text
    lines = [l.strip() for l in text.strip().split("\n") if l.strip()]
    # Remove any numbering artifacts (e.g. "1.", "12)", "- ", "* ")
    import re
    cleaned = []
    for line in lines:
        line = re.sub(r'^\d+[\.\)]\s*', '', line)
        line = re.sub(r'^[-\*]\s+', '', line)
        line = line.strip()
        if line:
            cleaned.append(line)
    return cleaned


def main():
    existing = load_existing_queries()
    n_mined = len(existing)
    n_to_generate = max(0, TARGET_TOTAL - n_mined)

    print(f"Mined queries: {n_mined}")
    print(f"Target total: {TARGET_TOTAL}")
    print(f"To generate: {n_to_generate}")

    if n_to_generate == 0:
        print("Already have enough queries, skipping generation.")
        return

    # Distribute evenly across branches and styles
    n_per_branch = n_to_generate // len(BRANCH_KEYS)
    n_per_style = max(1, n_per_branch // len(STYLES))

    print(f"Per branch: ~{n_per_branch} | Per style: ~{n_per_style}")
    print(f"API calls planned: {len(BRANCH_KEYS) * len(STYLES)}")

    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from env
    all_generated = []
    all_existing_lower = [q.lower().strip() for q in existing]

    for branch_key in BRANCH_KEYS:
        branch_desc = BRANCHES[branch_key]
        for style_key, style in STYLES.items():
            print(f"  {branch_key} × {style_key}...", end=" ", flush=True)

            queries = generate_batch(
                client, branch_key, branch_desc, style_key, style,
                n=n_per_style,
            )

            # Deduplicate against existing + already generated
            new_queries = []
            for q in queries:
                if not is_duplicate(q, all_existing_lower):
                    new_queries.append(q)
                    all_existing_lower.append(q.lower().strip())

            for q in new_queries:
                all_generated.append({
                    "query": q,
                    "source": "claude-generated",
                    "branch_hint": branch_key,
                    "style": style_key,
                })

            print(f"{len(new_queries)}/{len(queries)} kept")
            time.sleep(0.5)  # gentle rate limiting

    output_path = DATA_DIR / "generated_queries.jsonl"
    with open(output_path, "w") as f:
        for item in all_generated:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"\nGenerated: {len(all_generated)} unique queries")
    print(f"Saved to {output_path}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Set ANTHROPIC_API_KEY and run**

```bash
cd /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator
export ANTHROPIC_API_KEY="<your-key>"
.venv/bin/python generate_queries.py
```

Expected: ~80 API calls, generates ~2000-3500 queries after dedup. Takes a few minutes.

- [ ] **Step 3: Inspect output quality**

```bash
wc -l data/generated_queries.jsonl
# Check variety of styles
.venv/bin/python -c "
import json
from collections import Counter
with open('data/generated_queries.jsonl') as f:
    items = [json.loads(l) for l in f]
styles = Counter(i['style'] for i in items)
branches = Counter(i['branch_hint'] for i in items)
print(f'Total: {len(items)}')
print(f'Styles: {dict(styles)}')
print(f'Branches (min/max): {min(branches.values())}/{max(branches.values())}')
# Show samples per style
for s in 'ABCD':
    samples = [i['query'] for i in items if i['style'] == s][:3]
    print(f'\nStyle {s}:')
    for q in samples: print(f'  {q}')
"
```

Visually verify: diverse, French, oral register, appropriate styles.

- [ ] **Step 4: Commit**

```bash
git add benchmarks/tree-navigator/generate_queries.py
git commit -m "feat(tree-nav): Phase 2 — Claude-generated French utterances"
```

---

## Chunk 4: Phase 3 — Multi-Label Annotation

### Task 5: Create label_queries.py

**Files:**
- Create: `benchmarks/tree-navigator/label_queries.py`

This script:
1. Merges mined + generated queries into `data/all_queries.jsonl`
2. Sends batches of 10 queries to Claude for multi-label annotation (0/1 per branch)
3. Outputs `data/labeled_queries.jsonl` with full label vectors
4. Discards queries with 0 positive branches

- [ ] **Step 1: Write label_queries.py**

```python
#!/usr/bin/env python3
"""Phase 3: Multi-label annotation of all queries via Claude API."""

import json
import os
import time

import anthropic
from shared import BRANCHES, BRANCH_KEYS, DATA_DIR

MODEL = "claude-sonnet-4-20250514"
BATCH_SIZE = 10
MAX_RETRIES = 2

BRANCH_LIST_STR = "\n".join(
    f"- {key}: {desc}" for key, desc in BRANCHES.items()
)


def load_all_queries() -> list[dict]:
    """Load and merge mined + generated queries."""
    queries = []
    for filename in ["mined_utterances.jsonl", "generated_queries.jsonl"]:
        path = DATA_DIR / filename
        if path.exists():
            with open(path) as f:
                for line in f:
                    queries.append(json.loads(line))
    return queries


def build_annotation_prompt(batch: list[dict]) -> str:
    queries_block = "\n".join(
        f'{i+1}. "{item["query"]}"'
        for i, item in enumerate(batch)
    )

    prompt = f"""Tu es un annotateur de données pour un assistant vocal domestique.

Pour chaque phrase utilisateur ci-dessous, détermine quelles branches du profil utilisateur sont pertinentes.
Une branche est pertinente si la phrase révèle, demande, ou concerne cet aspect du profil.

Branches du profil :
{BRANCH_LIST_STR}

Phrases à annoter :
{queries_block}

Réponds UNIQUEMENT avec un JSON array. Chaque élément est un objet avec les clés étant les noms de branches et les valeurs 0 ou 1.
Exemple pour 2 phrases : [{{"Physical_Appearance": 0, "Physiological_Status": 1, ...}}, {{"Physical_Appearance": 1, ...}}]

IMPORTANT :
- Le array doit avoir exactement {len(batch)} éléments (un par phrase)
- Chaque élément doit avoir exactement {len(BRANCH_KEYS)} clés
- Utilise les noms de branches EXACTEMENT comme listés ci-dessus
- Une phrase peut être pertinente pour 0 à 5 branches (souvent 1-3)
- En cas de doute, marque 0"""

    return prompt


def annotate_batch(
    client: anthropic.Anthropic,
    batch: list[dict],
) -> list[dict] | None:
    """Annotate a batch of queries. Returns list of label dicts or None on failure."""
    prompt = build_annotation_prompt(batch)

    for attempt in range(MAX_RETRIES + 1):
        try:
            response = client.messages.create(
                model=MODEL,
                max_tokens=8192,
                messages=[{"role": "user", "content": prompt}],
            )
            text = response.content[0].text.strip()

            # Extract JSON array from response (handle markdown code blocks)
            if "```" in text:
                text = text.split("```")[1]
                if text.startswith("json"):
                    text = text[4:]
                text = text.strip()

            labels_list = json.loads(text)

            if len(labels_list) != len(batch):
                print(f"    ⚠ Got {len(labels_list)} labels for {len(batch)} queries, retry {attempt+1}")
                continue

            # Validate structure
            valid = True
            for labels in labels_list:
                if not isinstance(labels, dict):
                    valid = False
                    break
                for key in BRANCH_KEYS:
                    if key not in labels:
                        labels[key] = 0  # default to 0 if missing
                    elif labels[key] not in (0, 1):
                        labels[key] = int(bool(labels[key]))
            if not valid:
                print(f"    ⚠ Invalid structure, retry {attempt+1}")
                continue

            return labels_list

        except (json.JSONDecodeError, KeyError, IndexError) as e:
            print(f"    ⚠ Parse error: {e}, retry {attempt+1}")
            continue

    return None


def main():
    queries = load_all_queries()
    print(f"Total queries to annotate: {len(queries)}")

    # Save merged queries
    all_queries_path = DATA_DIR / "all_queries.jsonl"
    with open(all_queries_path, "w") as f:
        for item in queries:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")
    print(f"Merged queries saved to {all_queries_path}")

    # Check for existing progress (resume support)
    output_path = DATA_DIR / "labeled_queries.jsonl"
    already_done = 0
    if output_path.exists():
        with open(output_path) as f:
            already_done = sum(1 for _ in f)
        print(f"Resuming: {already_done} already labeled")
        queries = queries[already_done:]

    client = anthropic.Anthropic()
    n_batches = (len(queries) + BATCH_SIZE - 1) // BATCH_SIZE
    labeled_count = 0
    skipped_count = 0

    with open(output_path, "a") as out_f:
        for batch_idx in range(n_batches):
            start = batch_idx * BATCH_SIZE
            end = min(start + BATCH_SIZE, len(queries))
            batch = queries[start:end]

            print(f"Batch {batch_idx+1}/{n_batches} ({start}-{end})...", end=" ", flush=True)

            labels_list = annotate_batch(client, batch)

            if labels_list is None:
                print("FAILED — skipping batch")
                skipped_count += len(batch)
                continue

            for item, labels in zip(batch, labels_list):
                n_positive = sum(labels.values())
                if n_positive == 0:
                    skipped_count += 1
                    continue
                labeled_item = {
                    "query": item["query"],
                    "labels": labels,
                    "source": item.get("source", "unknown"),
                    "n_positive": n_positive,
                }
                out_f.write(json.dumps(labeled_item, ensure_ascii=False) + "\n")
                labeled_count += 1

            print(f"OK ({sum(sum(l.values()) for l in labels_list)} positives)")
            time.sleep(0.3)

    print(f"\nLabeled: {labeled_count} queries")
    print(f"Skipped (0 branches or failed): {skipped_count}")
    print(f"Saved to {output_path}")

    # Stats
    with open(output_path) as f:
        items = [json.loads(l) for l in f]
    avg_pos = sum(i["n_positive"] for i in items) / len(items) if items else 0
    print(f"Avg positive branches per query: {avg_pos:.1f}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run label_queries.py**

```bash
cd /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator
export ANTHROPIC_API_KEY="<your-key>"
.venv/bin/python label_queries.py
```

Expected: ~300-400 API calls (4000 queries / 10 per batch). Takes 5-15 minutes.
Output: `data/labeled_queries.jsonl` with avg 2-3 positive branches per query.

The script supports resume — if interrupted, re-run and it picks up where it left off.

- [ ] **Step 3: Verify label quality**

```bash
.venv/bin/python -c "
import json
from collections import Counter
with open('data/labeled_queries.jsonl') as f:
    items = [json.loads(l) for l in f]
print(f'Total labeled: {len(items)}')
avg = sum(i['n_positive'] for i in items) / len(items)
print(f'Avg positive branches: {avg:.1f}')
dist = Counter(i['n_positive'] for i in items)
print(f'Distribution: {dict(sorted(dist.items()))}')
# Branch coverage
branch_counts = Counter()
for i in items:
    for k, v in i['labels'].items():
        if v == 1:
            branch_counts[k] += 1
print(f'\nBranch coverage:')
for k, v in branch_counts.most_common():
    print(f'  {k}: {v}')
"
```

Check: avg positives ~2-3, all 20 branches have reasonable coverage (>50 each).

- [ ] **Step 4: Commit**

```bash
git add benchmarks/tree-navigator/label_queries.py
git commit -m "feat(tree-nav): Phase 3 — Claude multi-label annotation"
```

---

## Chunk 5: Phase 4 — Training Pair Construction

### Task 6: Create build_training_pairs.py

**Files:**
- Create: `benchmarks/tree-navigator/build_training_pairs.py`

This script:
1. Computes branch similarity matrix using sentence-transformers
2. For each labeled query, constructs positive + hard/easy negative pairs
3. Splits into train/eval at query level (85/15)
4. Outputs final training files

- [ ] **Step 1: Write build_training_pairs.py**

```python
#!/usr/bin/env python3
"""Phase 4: Build training pairs with hard/easy negatives + train/eval split."""

import json
import random
from collections import Counter

import numpy as np
from sentence_transformers import SentenceTransformer
from shared import BRANCHES, BRANCH_KEYS, DATA_DIR

SEED = 42
TRAIN_RATIO = 0.85


def compute_branch_similarity() -> dict:
    """Compute cosine similarity between all branch descriptions."""
    print("Computing branch similarity matrix...")
    model = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")
    descriptions = [BRANCHES[k] for k in BRANCH_KEYS]
    embeddings = model.encode(descriptions, normalize_embeddings=True)

    similarity = {}
    for i, ki in enumerate(BRANCH_KEYS):
        similarity[ki] = {}
        for j, kj in enumerate(BRANCH_KEYS):
            similarity[ki][kj] = float(np.dot(embeddings[i], embeddings[j]))

    # Save matrix
    sim_path = DATA_DIR / "branch_similarity.json"
    with open(sim_path, "w") as f:
        json.dump(similarity, f, indent=2)
    print(f"Saved to {sim_path}")

    return similarity


def get_hard_negative_candidates(
    positive_branches: list[str],
    all_labels: dict,
    similarity: dict,
) -> list[str]:
    """Get branches ranked by similarity to positive branches (label=0 only)."""
    negative_branches = [k for k in BRANCH_KEYS if all_labels.get(k, 0) == 0]

    # For each negative branch, find max similarity to any positive branch
    scored = []
    for neg_key in negative_branches:
        max_sim = max(similarity[neg_key][pos_key] for pos_key in positive_branches)
        scored.append((neg_key, max_sim))

    # Sort by similarity descending (hardest negatives first)
    scored.sort(key=lambda x: x[1], reverse=True)
    return [k for k, _ in scored]


def build_pairs(labeled_queries: list[dict], similarity: dict) -> list[dict]:
    """Build training pairs with 1:1 pos/neg ratio, 50/50 hard/easy negatives."""
    random.seed(SEED)
    pairs = []

    for item in labeled_queries:
        query = item["query"]
        labels = item["labels"]
        source = item.get("source", "unknown")

        positive_branches = [k for k in BRANCH_KEYS if labels.get(k, 0) == 1]
        if not positive_branches:
            continue

        # Add all positive pairs
        for branch_key in positive_branches:
            pairs.append({
                "query": query,
                "branch_key": branch_key,
                "branch_description": BRANCHES[branch_key],
                "label": 1,
                "pair_type": "positive",
                "source": source,
            })

        # Sample negatives: same count as positives
        n_neg = len(positive_branches)
        n_hard = (n_neg + 1) // 2  # ceil(n/2)
        n_easy = n_neg - n_hard

        hard_candidates = get_hard_negative_candidates(
            positive_branches, labels, similarity,
        )

        # Hard negatives: top of ranked list
        hard_negs = hard_candidates[:n_hard]
        # Easy negatives: random from the rest
        easy_pool = hard_candidates[n_hard:]
        easy_negs = random.sample(easy_pool, min(n_easy, len(easy_pool)))

        for branch_key in hard_negs:
            pairs.append({
                "query": query,
                "branch_key": branch_key,
                "branch_description": BRANCHES[branch_key],
                "label": 0,
                "pair_type": "hard_neg",
                "source": source,
            })

        for branch_key in easy_negs:
            pairs.append({
                "query": query,
                "branch_key": branch_key,
                "branch_description": BRANCHES[branch_key],
                "label": 0,
                "pair_type": "easy_neg",
                "source": source,
            })

    return pairs


def split_by_query(pairs: list[dict], train_ratio: float) -> tuple[list, list]:
    """Split pairs into train/eval at query level (no leakage)."""
    random.seed(SEED)

    # Group by query
    query_to_pairs = {}
    for p in pairs:
        q = p["query"]
        if q not in query_to_pairs:
            query_to_pairs[q] = []
        query_to_pairs[q].append(p)

    queries = list(query_to_pairs.keys())
    random.shuffle(queries)

    split_idx = int(len(queries) * train_ratio)
    train_queries = set(queries[:split_idx])

    train_pairs = []
    eval_pairs = []
    for q, ps in query_to_pairs.items():
        if q in train_queries:
            train_pairs.extend(ps)
        else:
            eval_pairs.extend(ps)

    return train_pairs, eval_pairs


def main():
    # Load labeled queries
    labeled_path = DATA_DIR / "labeled_queries.jsonl"
    with open(labeled_path) as f:
        labeled = [json.loads(l) for l in f]
    print(f"Labeled queries: {len(labeled)}")

    # Compute or load similarity matrix
    sim_path = DATA_DIR / "branch_similarity.json"
    if sim_path.exists():
        with open(sim_path) as f:
            similarity = json.load(f)
        print("Loaded existing branch similarity matrix")
    else:
        similarity = compute_branch_similarity()

    # Build pairs
    pairs = build_pairs(labeled, similarity)
    print(f"\nTotal pairs: {len(pairs)}")

    # Stats
    type_counts = Counter(p["pair_type"] for p in pairs)
    label_counts = Counter(p["label"] for p in pairs)
    print(f"By type: {dict(type_counts)}")
    print(f"By label: {dict(label_counts)}")

    # Split
    train_pairs, eval_pairs = split_by_query(pairs, TRAIN_RATIO)
    print(f"\nTrain: {len(train_pairs)} | Eval: {len(eval_pairs)}")

    # Save all
    for name, data in [
        ("training_pairs.jsonl", pairs),
        ("training_pairs_train.jsonl", train_pairs),
        ("training_pairs_eval.jsonl", eval_pairs),
    ]:
        path = DATA_DIR / name
        with open(path, "w") as f:
            for item in data:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        print(f"Saved {path} ({len(data)} pairs)")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run build_training_pairs.py**

```bash
cd /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator
.venv/bin/python build_training_pairs.py
```

Expected: ~20,000-25,000 total pairs, 85/15 split, balanced pos/neg.

- [ ] **Step 3: Verify output stats**

```bash
wc -l data/training_pairs*.jsonl
.venv/bin/python -c "
import json
with open('data/training_pairs.jsonl') as f:
    pairs = [json.loads(l) for l in f]
from collections import Counter
print(f'Total: {len(pairs)}')
print(f'Labels: {Counter(p[\"label\"] for p in pairs)}')
print(f'Types: {Counter(p[\"pair_type\"] for p in pairs)}')
print(f'Sources: {Counter(p[\"source\"] for p in pairs)}')
"
```

Check: ~50/50 pos/neg, hard_neg ≈ easy_neg count.

- [ ] **Step 4: Commit**

```bash
git add benchmarks/tree-navigator/build_training_pairs.py
git commit -m "feat(tree-nav): Phase 4 — training pair construction with hard negatives"
```

---

## Chunk 6: Phase 5 — Validation Sample + Final Verification

### Task 7: Create sample_for_review.py

**Files:**
- Create: `benchmarks/tree-navigator/sample_for_review.py`

- [ ] **Step 1: Write sample_for_review.py**

```python
#!/usr/bin/env python3
"""Phase 5: Sample queries for human validation."""

import json
import random
from shared import BRANCH_KEYS, DATA_DIR

SAMPLE_SIZE = 200
SEED = 42


def main():
    labeled_path = DATA_DIR / "labeled_queries.jsonl"
    with open(labeled_path) as f:
        items = [json.loads(l) for l in f]

    random.seed(SEED)

    # Stratify by source
    by_source = {}
    for item in items:
        src = item.get("source", "unknown")
        if src not in by_source:
            by_source[src] = []
        by_source[src].append(item)

    # Sample proportionally from each source
    sample = []
    for src, src_items in by_source.items():
        n = max(1, int(SAMPLE_SIZE * len(src_items) / len(items)))
        sampled = random.sample(src_items, min(n, len(src_items)))
        sample.extend(sampled)

    # Trim to exact size
    random.shuffle(sample)
    sample = sample[:SAMPLE_SIZE]

    # Write TSV for easy review
    output_path = DATA_DIR / "review_sample.tsv"
    with open(output_path, "w") as f:
        # Header
        header = ["query", "source", "n_positive"] + BRANCH_KEYS
        f.write("\t".join(header) + "\n")

        for item in sample:
            row = [
                item["query"],
                item.get("source", ""),
                str(item.get("n_positive", 0)),
            ]
            for key in BRANCH_KEYS:
                row.append(str(item["labels"].get(key, 0)))
            f.write("\t".join(row) + "\n")

    print(f"Saved {len(sample)} queries to {output_path}")
    print(f"Open in a spreadsheet to review labels.")
    print(f"\nSources in sample:")
    from collections import Counter
    for src, count in Counter(i.get("source") for i in sample).most_common():
        print(f"  {src}: {count}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run sample_for_review.py**

```bash
.venv/bin/python sample_for_review.py
```

Expected: `data/review_sample.tsv` with 200 rows + header. Can be opened in LibreOffice/Excel.

- [ ] **Step 3: Commit**

```bash
git add benchmarks/tree-navigator/sample_for_review.py
git commit -m "feat(tree-nav): Phase 5 — validation sample extraction"
```

### Task 8: Final pipeline summary

- [ ] **Step 1: Run full stats check**

```bash
cd /home/pierre-wagniart/project/ARCOS/benchmarks/tree-navigator
echo "=== Pipeline Output ==="
echo "Phase 1 (mined):" && wc -l data/mined_utterances.jsonl
echo "Phase 2 (generated):" && wc -l data/generated_queries.jsonl
echo "Phase 3 (labeled):" && wc -l data/labeled_queries.jsonl
echo "Phase 4 (pairs):" && wc -l data/training_pairs.jsonl
echo "Phase 4 (train):" && wc -l data/training_pairs_train.jsonl
echo "Phase 4 (eval):" && wc -l data/training_pairs_eval.jsonl
echo "Phase 5 (review):" && wc -l data/review_sample.tsv
```

- [ ] **Step 2: Commit all data files**

```bash
git add benchmarks/tree-navigator/data/
git commit -m "data(tree-nav): training data pipeline output — Phase 1-5"
```

---

## Success Criteria Checklist

- [ ] `data/training_pairs.jsonl` has 20,000-25,000 pairs
- [ ] ~50/50 positive/negative ratio
- [ ] Hard negatives ≈ easy negatives count
- [ ] Avg 2-3 positive branches per query
- [ ] All 20 branches represented with >50 positives each
- [ ] Train/eval split at query level (no leakage)
- [ ] `review_sample.tsv` ready for human review
