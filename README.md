# ArCoS — Artificial Cognitive System

Agent IA autonome avec personnalité, mémoire, opinions et prise d'initiative. Interaction vocale sur Raspberry Pi 5.

## Démarrage

```bash
cd ARCOS
cp .env.example .env   # Remplir au minimum MISTRALAI_API_KEY
docker compose up --build
```

Ou laisser le **wizard interactif** guider la configuration au premier lancement :

```bash
mvn clean package -DskipTests
java -jar target/ARCOS-0.0.1-SNAPSHOT.jar --setup
```

### Séquence de boot

```
Bannière ASCII  →  Wizard (si config manquante)  →  Spring Boot
    →  Rapport de démarrage (état des services)  →  Salutation de personnalité
    →  Boucle événementielle
```

## Architecture

```
WakeWord → Capture audio → Speech-to-Text (faster-whisper)
    → EventQueue (priorité HIGH/MEDIUM/LOW)
    → Orchestrator → LLM (Mistral AI, streaming)
    → TTS (Piper) + pipeline personnalité async
```

| Package | Rôle |
|---------|------|
| `Orchestrator/` | Coordinateur central, consomme les événements |
| `EventBus/` | File d'attente à priorité |
| `LLM/` | Client Mistral AI, prompts, DTOs |
| `Memory/` | Court terme (contexte) + long terme (Qdrant) |
| `Personality/` | Opinions, désirs, humeur (PAD), valeurs Schwartz |
| `IO/` | Audio, TTS Piper, feedback LED |
| `Tools/` | Brave Search, Google Calendar, Python |
| `Setup/` | Wizard de configuration (pré-Spring) |
| `Boot/` | Bannière, rapport de démarrage, salutation |
| `PlannedAction/` | Actions programmées (ReWOO) |

### Persistance

Trois collections Qdrant : **Memories**, **Opinions**, **Desires**. Les désirs intenses (≥ 0.8) déclenchent des initiatives autonomes.

## Stack

Java 21, Spring Boot 3.5.3, Mistral AI, Qdrant, Porcupine, faster-whisper, Piper, Docker.

## Développement

```bash
docker compose up qdrant faster-whisper   # Services externes
mvn spring-boot:run                       # App locale

mvn test                                  # Tous les tests
mvn test -Dtest=MemoryServiceTest         # Un test
```

## Personnalités

CALCIFER · K2SO · GLADOS · DEFAULT — choix au wizard ou dans `application-local.yaml`.
