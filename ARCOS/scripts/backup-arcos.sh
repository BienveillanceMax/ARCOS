#!/usr/bin/env bash
# backup-arcos.sh — Sauvegarde ARCOS : snapshots Qdrant (API HTTP) + data/ + config
#
# Usage: ./scripts/backup-arcos.sh
#
# Variables d'environnement (optionnelles) :
#   ARCOS_QDRANT_URL   URL de l'API REST Qdrant   (défaut: http://localhost:6333)
#   ARCOS_BACKUP_DIR   Répertoire des archives    (défaut: ~/arcos-backups)
#   ARCOS_BACKUP_KEEP  Rétention keep-last-N      (défaut: 7)
#
# Contenu de l'archive : snapshots par collection Qdrant (Memories, Opinions,
# Desires, ...découvertes dynamiquement), data/*.json, application-local.yaml,
# .env. L'archive contient des secrets → chmod 600, à garder sur stockage local.
#
# Restauration : voir le bloc « Restauration » en fin de script.

set -euo pipefail

QDRANT_URL="${ARCOS_QDRANT_URL:-http://localhost:6333}"
BACKUP_DIR="${ARCOS_BACKUP_DIR:-$HOME/arcos-backups}"
KEEP="${ARCOS_BACKUP_KEEP:-7}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p "$BACKUP_DIR"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# ── Qdrant : fail-fast si injoignable ─────────────────────────────────────────
# Un backup sans la mémoire vectorielle est un demi-backup ; qdrant est en
# restart:unless-stopped donc quasi toujours up. Le timer systemd rend l'échec
# visible (journalctl --user -u arcos-backup).
if ! curl -fsS --max-time 5 -o /dev/null "$QDRANT_URL/readyz"; then
    echo "ERREUR: Qdrant injoignable sur $QDRANT_URL — sauvegarde annulée." >&2
    exit 1
fi

# ── Snapshots par collection (API HTTP, sans toucher au bind mount root) ──────
mkdir -p "$STAGE/qdrant"
COLLECTIONS="$(curl -fsS "$QDRANT_URL/collections" \
    | python3 -c "import sys,json; print('\n'.join(c['name'] for c in json.load(sys.stdin)['result']['collections']))")"

if [ -z "$COLLECTIONS" ]; then
    echo "AVERTISSEMENT: aucune collection Qdrant trouvée."
fi

NB_COLLECTIONS=0
while IFS= read -r c; do
    [ -z "$c" ] && continue
    echo "Snapshot de la collection '$c'..."
    SNAP="$(curl -fsS -X POST "$QDRANT_URL/collections/$c/snapshots?wait=true" \
        | python3 -c "import sys,json; print(json.load(sys.stdin)['result']['name'])")"
    curl -fsS "$QDRANT_URL/collections/$c/snapshots/$SNAP" -o "$STAGE/qdrant/$c.snapshot"
    # Nettoyage côté serveur (le snapshot vit sinon dans le conteneur)
    curl -fsS -X DELETE "$QDRANT_URL/collections/$c/snapshots/$SNAP" -o /dev/null
    NB_COLLECTIONS=$((NB_COLLECTIONS + 1))
done <<< "$COLLECTIONS"

# ── Fichiers d'état et config ─────────────────────────────────────────────────
# NB : data/*.json est copié à chaud — fenêtre de corruption minuscule (écritures
# petites, backup nocturne), assumée pour un homelab.
if [ -d data ]; then
    mkdir -p "$STAGE/data"
    find data -maxdepth 1 -name '*.json' -exec cp {} "$STAGE/data/" \;
fi
[ -f application-local.yaml ] && cp application-local.yaml "$STAGE/"
[ -f .env ] && cp .env "$STAGE/"

# ── Archive + rétention ───────────────────────────────────────────────────────
ARCHIVE="$BACKUP_DIR/arcos-backup-$(date +%Y%m%d-%H%M%S).tar.gz"
tar -czf "$ARCHIVE" -C "$STAGE" .
chmod 600 "$ARCHIVE"          # contient .env (clés API)
tar -tzf "$ARCHIVE" > /dev/null   # vérification d'intégrité

# Rétention : garder les $KEEP archives les plus récentes
ls -1t "$BACKUP_DIR"/arcos-backup-*.tar.gz 2>/dev/null \
    | tail -n +"$((KEEP + 1))" | xargs -r rm -f --

echo "OK: $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1), $NB_COLLECTIONS collections Qdrant)"

# ── Restauration ──────────────────────────────────────────────────────────────
# 1. Extraire l'archive :
#      mkdir /tmp/restore && tar -xzf arcos-backup-<ts>.tar.gz -C /tmp/restore
# 2. Restaurer chaque collection Qdrant (recrée/écrase la collection) :
#      curl -X POST "http://localhost:6333/collections/<nom>/snapshots/upload?priority=snapshot" \
#           -H 'Content-Type: multipart/form-data' \
#           -F "snapshot=@/tmp/restore/qdrant/<nom>.snapshot"
# 3. Recopier les fichiers d'état dans ARCOS/ (app arrêtée) :
#      cp /tmp/restore/data/*.json ARCOS/data/
#      cp /tmp/restore/application-local.yaml /tmp/restore/.env ARCOS/
