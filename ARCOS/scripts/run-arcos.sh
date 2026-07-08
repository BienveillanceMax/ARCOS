#!/usr/bin/env bash
# run-arcos.sh — Lance ARCOS : services Docker + attente santé + app native
#
# Usage: ./scripts/run-arcos.sh [--build] [args passés au jar, ex: --setup]
#   --build   Force un rebuild du jar (mvn package -DskipTests) avant lancement.
#
# Lancé depuis un vrai terminal, la TUI Lanterna fonctionne normalement.
# Sans TTY (ssh sans -t, nohup), le fallback console d'ArcosApplication s'applique.

set -euo pipefail

# Le CWD compte : application-local.yaml, data/ et logs/ sont relatifs à ARCOS/.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ── Arguments ─────────────────────────────────────────────────────────────────
BUILD=0
APP_ARGS=()
for arg in "$@"; do
    case "$arg" in
        --build) BUILD=1 ;;
        *) APP_ARGS+=("$arg") ;;
    esac
done

# ── Environnement (.env) ──────────────────────────────────────────────────────
# C'est CE sourcing qui résout les ${MISTRALAI_API_KEY} & co d'application.properties
# (pas de spring-dotenv dans le projet : Spring lit l'environnement du process).
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source ./.env
    set +a
else
    echo "AVERTISSEMENT: pas de .env — MISTRALAI_API_KEY doit être exportée dans l'environnement."
fi

# ── Services Docker ───────────────────────────────────────────────────────────
echo "=== Démarrage des services Docker (qdrant, whisper-cpp, radicale) ==="
docker compose up -d

# Attente santé : boucle curl avec deadline.
# url, deadline (s), nom ; retourne 0 si le service répond avant la deadline.
wait_http() {
    local url="$1" deadline="$2" name="$3"
    local start elapsed
    start=$(date +%s)
    while true; do
        if curl -fsS --max-time 2 -o /dev/null "$url"; then
            elapsed=$(( $(date +%s) - start ))
            echo "  $name : prêt (${elapsed}s)"
            return 0
        fi
        elapsed=$(( $(date +%s) - start ))
        if [ "$elapsed" -ge "$deadline" ]; then
            return 1
        fi
        sleep 1
    done
}

# Qdrant est une dépendance dure : QdrantClientProvider ne retente que ~60s puis
# fait échouer le contexte Spring. Attendre ici évite de consommer ces retries
# sur un démarrage à froid (le comportement hard-dependency reste inchangé).
if ! wait_http "http://localhost:6333/readyz" 90 "qdrant"; then
    echo "ERREUR: Qdrant ne répond pas sur localhost:6333 après 90s. Voir: docker compose logs qdrant"
    exit 1
fi

# whisper-cpp : backend STT actif (arcos.stt.backend=WHISPER_CPP).
if ! wait_http "http://localhost:8090/" 60 "whisper-cpp"; then
    echo "ERREUR: whisper-cpp ne répond pas sur localhost:8090 après 60s. Voir: docker compose logs whisper-cpp"
    exit 1
fi

# Radicale : non bloquant, le calendrier est une feature optionnelle.
if ! wait_http "http://localhost:5232/.web" 15 "radicale"; then
    echo "AVERTISSEMENT: Radicale ne répond pas sur localhost:5232 — le calendrier sera indisponible."
fi

# ── Jar ───────────────────────────────────────────────────────────────────────
find_jar() {
    ls -t target/ARCOS-*.jar 2>/dev/null | grep -v '\.original' | head -1 || true
}

JAR="$(find_jar)"
if [ -z "$JAR" ] || [ "$BUILD" -eq 1 ]; then
    echo "=== Build du jar (mvn package -DskipTests) ==="
    mvn -q -DskipTests package
    JAR="$(find_jar)"
    if [ -z "$JAR" ]; then
        echo "ERREUR: aucun jar trouvé dans target/ après le build."
        exit 1
    fi
elif [ -n "$(find src pom.xml -newer "$JAR" -print -quit 2>/dev/null)" ]; then
    # Warning seulement — pas de rebuild automatique (démarrage prévisible).
    echo "AVERTISSEMENT: des sources sont plus récentes que $JAR — relancer avec --build pour reconstruire."
fi

# ── Lancement ─────────────────────────────────────────────────────────────────
# exec : le process java hérite du TTY → TerminalCapabilities détecte le plein
# écran et la TUI Lanterna fonctionne.
echo "=== Lancement d'ARCOS ($JAR) ==="
exec java -jar "$JAR" ${APP_ARGS[@]+"${APP_ARGS[@]}"}
