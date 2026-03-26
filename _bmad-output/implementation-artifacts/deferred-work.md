# Deferred Work

## Deferred from: code review Sprint 8 (2026-03-26)

- **dispatch() sans clause else pour EventType inconnus** — Le if-else chain dans Orchestrator.dispatch() ne log pas les EventTypes non reconnus. Pattern pré-existant. [Orchestrator.java:129-184]
- **Prompt injection dans buildSummaryPrompt** — ConversationSummaryService envoie le texte utilisateur brut comme SystemMessage. Risque faible (Pi personnel). [ConversationSummaryService.java:43-46]
- **planned-actions.json contient des données runtime** — Données de test/runtime commités. Devrait être gitignored. [ARCOS/data/planned-actions.json]
- **install-tailscale.sh : snap path sans systemctl enable** — Le chemin snap ne lance pas `systemctl enable --now tailscaled`. [scripts/install-tailscale.sh]

## Deferred from: code review of sprint 7 (2026-03-26)

- **BaseVectorRepository missing @CircuitBreaker** — `BaseVectorRepository.save()` and `search()` have `@RateLimiter(name = "mistral_free")` but no `@CircuitBreaker`. Embedding API failures are not protected by circuit breaker. Pre-existing, not introduced by sprint 7. [BaseVectorRepository.java:60-72]
- **OpinionService.getOpinionFromMemoryEntry NPE on null LLM response** — `llmClient.generateOpinionResponse(prompt)` can return null, causing NPE on the next line. Caught by generic exception handler but logged with misleading "Erreur de parsing" message. Pre-existing. [OpinionService.java:70-71]
- **DesireEntry.fromDesireResponse doesn't set reasoning field** — Desires created from LLM responses lose their reasoning text. The `reasoning` metadata in Qdrant is always empty string for LLM-created desires. Pre-existing. [DesireEntry.java:20-31]
