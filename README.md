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
docker compose up qdrant faster-whisper   # Services externes
mvn spring-boot:run                       # App locale

mvn test                                  # Tous les tests
mvn test -Dtest=MemoryServiceTest         # Un test
```

## Tailscale — Accès distant

[Tailscale](https://tailscale.com) crée un VPN mesh entre vos appareils (Pi, téléphone, PC). Une fois installé, le calendrier CalDAV (Radicale) est accessible depuis n'importe où, sans ouvrir de ports sur votre box.

### Installation (une seule fois, sur le Pi)

```bash
sudo bash scripts/install-tailscale.sh
```

Le script installe Tailscale, active le service systemd, et affiche un lien d'authentification à ouvrir dans un navigateur pour lier le Pi à votre compte Tailscale (gratuit, jusqu'à 100 appareils sur [login.tailscale.com](https://login.tailscale.com)).

### Démarrer / vérifier le statut

```bash
sudo tailscale up          # Connecter au réseau mesh
tailscale status           # Voir les appareils connectés
tailscale ip -4            # Afficher l'IP Tailscale du Pi (100.x.y.z)
```

### Utilisation — accéder au calendrier à distance

Le Pi obtient une IP stable `100.x.y.z`. Depuis tout appareil sur votre réseau Tailscale :

```
http://100.x.y.z:5232     ← Interface web Radicale
```

**Synchroniser avec votre téléphone (DAVx5) :**

1. Installer Tailscale sur le téléphone, se connecter avec le même compte
2. Installer [DAVx5](https://www.davx5.com/) (gratuit sur [F-Droid](https://f-droid.org/packages/at.bitfire.davdroid/), payant sur Play Store)
3. Ajouter un compte CalDAV : `http://100.x.y.z:5232/arcos/calendar/`
4. Identifiants : `arcos` / `arcos`
5. Synchroniser — le calendrier apparaît dans votre app calendrier

### Arrêter / redémarrer

```bash
sudo tailscale down        # Déconnecter (le Pi reste installé, juste hors-ligne)
sudo systemctl stop tailscaled   # Arrêter complètement le service

sudo tailscale up          # Reconnecter (pas besoin de ré-authentifier)
```

## Personnalités

CALCIFER · K2SO · GLADOS · DEFAULT — choix au wizard ou dans `application-local.yaml`.
