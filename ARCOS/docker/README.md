# Déploiement ARCOS

## Modèle : app native + services Docker

L'app ARCOS tourne **nativement** (hors Docker) : elle a besoin d'un accès direct
à l'audio (PipeWire/PulseAudio), au Bluetooth (D-Bus système) et à un terminal
interactif (TUI Lanterna). Seuls les services réseau dont elle dépend tournent en
Docker (`docker-compose.yml`) ; l'app les joint via `localhost:<port>`.

Voir aussi la note de déploiement en fin de `docker-compose.yml` et `CLAUDE.md`.

## Lancement

```bash
./scripts/run-arcos.sh            # canonique : compose up -d + attente santé + jar
./scripts/run-arcos.sh --build    # force un rebuild du jar avant lancement
```

Manuel (dev) : `docker compose up -d` puis `mvn spring-boot:run`.

Logs persistants : `logs/arcos.log` (rotation 10 Mo / 14 jours / 200 Mo max).
En mode TUI la console est muette pendant le boot — le fichier est la seule trace.

## Services

| Service | Image | Port(s) | Données | Rôle |
|---------|-------|---------|---------|------|
| `qdrant` | qdrant/qdrant:v1.11.4 | 6333 (HTTP), 6334 (gRPC) | bind mount `./qdrant_storage` | Mémoire vectorielle (Memories, Opinions, Desires) |
| `whisper-cpp` | saririus/whisper-cpp-vulkan | 8090→8080 | modèle dans l'image | STT actif (Vulkan, `arcos.stt.backend=WHISPER_CPP`) |
| `faster-whisper` | fedirz/faster-whisper-server:latest-cpu | 8000 | modèle préchargé | STT alternatif (CPU) — **profil**, ne démarre pas par défaut |
| `radicale` | tomsquest/docker-radicale | 5232 | volume `radicale_data` | Calendrier CalDAV |

Démarrer faster-whisper (benchmarks / backend FASTER_WHISPER) :

```bash
docker compose --profile faster-whisper up -d
```

Rebuild de l'image whisper-cpp (télécharge ggml-large-v3-turbo, ~1,5 Go) :

```bash
docker build -f docker/whisper-cpp-vulkan.Dockerfile -t saririus/whisper-cpp-vulkan:latest .
```

## Sauvegardes

`scripts/backup-arcos.sh` archive dans `~/arcos-backups/` (rétention : 7 archives) :

- les collections Qdrant via l'API snapshots (sans toucher au bind mount root) ;
- `data/*.json` (persona tree, actions planifiées, historique…) ;
- `application-local.yaml` et `.env` (**secrets** → archives en mode 600, à garder
  sur stockage local de confiance).

Surcharges : `ARCOS_BACKUP_DIR`, `ARCOS_BACKUP_KEEP`, `ARCOS_QDRANT_URL`.

### Planification (timer systemd utilisateur, quotidien à ~03h47)

```bash
mkdir -p ~/.config/systemd/user
cp scripts/systemd/arcos-backup.* ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now arcos-backup.timer
sudo loginctl enable-linger "$USER"   # une seule fois : timers actifs sans session ouverte
```

Suivi : `systemctl --user list-timers arcos-backup.timer` ;
échecs visibles via `journalctl --user -u arcos-backup`.

NB : `arcos-backup.service` contient le chemin absolu du dépôt — l'adapter si le
dépôt change d'emplacement (ex. migration UM890).

### Restauration

```bash
mkdir /tmp/restore && tar -xzf ~/arcos-backups/arcos-backup-<ts>.tar.gz -C /tmp/restore

# Chaque collection Qdrant (recrée/écrase la collection) :
curl -X POST "http://localhost:6333/collections/<nom>/snapshots/upload?priority=snapshot" \
     -H 'Content-Type: multipart/form-data' \
     -F "snapshot=@/tmp/restore/qdrant/<nom>.snapshot"

# Fichiers d'état (app arrêtée) :
cp /tmp/restore/data/*.json data/
cp /tmp/restore/application-local.yaml /tmp/restore/.env .
```
