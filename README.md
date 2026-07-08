# ArCoS — Artificial Cognitive System

Agent IA autonome avec personnalité, mémoire, opinions et prise d'initiative. Interaction vocale sur Raspberry Pi 5.

## Démarrage

```bash
cd ARCOS
cp .env.example .env     # Remplir au minimum MISTRALAI_API_KEY
./scripts/run-arcos.sh   # Services Docker + attente santé + app native
```

L'app tourne **nativement** (audio, Bluetooth, TUI) ; seuls les services réseau
(qdrant, whisper-cpp, radicale) tournent en Docker. Logs persistants : `logs/arcos.log`.

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
WakeWord → Capture audio → Speech-to-Text (whisper.cpp)
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
| `Tools/` | Brave Search, Calendrier (CalDAV), Python |
| `Setup/` | Wizard de configuration (pré-Spring) |
| `Boot/` | Bannière, rapport de démarrage, salutation |
| `PlannedAction/` | Actions programmées (ReWOO) |

### Persistance

Trois collections Qdrant : **Memories**, **Opinions**, **Desires**. Les désirs intenses (≥ 0.8) déclenchent des initiatives autonomes.

## Stack

Java 21, Spring Boot 3.5.3, Mistral AI, Qdrant, Porcupine, faster-whisper, Piper, Docker.

## Développement

```bash
docker compose up -d                      # Services externes (qdrant, whisper-cpp, radicale)
mvn spring-boot:run                       # App locale

mvn test                                  # Tous les tests
mvn test -Dtest=MemoryServiceTest         # Un test
```

Le backend STT alternatif faster-whisper est derrière un profil compose :
`docker compose --profile faster-whisper up -d` (requis seulement si `arcos.stt.backend=FASTER_WHISPER`).

## Sauvegardes

`scripts/backup-arcos.sh` archive les collections Qdrant (API snapshots), `data/*.json`
et la config dans `~/arcos-backups/` (rétention 7 jours). Planification quotidienne via
timer systemd : voir [ARCOS/docker/README.md](ARCOS/docker/README.md).

## Accès distant (Tailscale)

[Tailscale](https://tailscale.com) crée un VPN mesh entre vos appareils, sans ouvrir de ports. Une fois installé sur le Pi et votre téléphone, le calendrier Radicale est accessible à distance via [DAVx5](https://www.davx5.com/) (gratuit sur [F-Droid](https://f-droid.org/packages/at.bitfire.davdroid/)).

```bash
sudo snap install tailscale   # ou: sudo bash scripts/install-tailscale.sh
sudo tailscale up             # affiche un lien d'auth à ouvrir dans un navigateur
tailscale ip -4               # → 100.x.y.z (IP stable du Pi)
```

URL CalDAV pour DAVx5 : `http://100.x.y.z:5232/arcos/calendar/` — identifiants `arcos` / `arcos`.

## Personnalités

CALCIFER · K2SO · GLADOS · DEFAULT — choix au wizard ou dans `application-local.yaml`.
