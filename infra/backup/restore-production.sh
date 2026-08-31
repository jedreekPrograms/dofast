#!/usr/bin/env bash
set -euo pipefail

umask 077

COMPOSE_FILE="${DOFAST_COMPOSE_FILE:-infra/compose/compose.prod.yaml}"
BACKUP_DIR="${DOFAST_BACKUP_DIR:?DOFAST_BACKUP_DIR is required}"
DB_SERVICE="${DOFAST_DB_SERVICE:-db}"
API_SERVICE="${DOFAST_API_SERVICE:-api}"
TARGET_DB="${DOFAST_RESTORE_DB:?DOFAST_RESTORE_DB is required}"
TARGET_ATTACHMENT_ROOT="${DOFAST_RESTORE_ATTACHMENT_ROOT:?DOFAST_RESTORE_ATTACHMENT_ROOT is required}"
CONFIRM="${DOFAST_RESTORE_CONFIRM:-}"

if [[ "$CONFIRM" != "RESTORE_DOFAST_BACKUP" ]]; then
  echo "Refusing restore: set DOFAST_RESTORE_CONFIRM=RESTORE_DOFAST_BACKUP" >&2
  exit 1
fi

for file in manifest.txt SHA256SUMS postgres.dump attachments.tar.gz; do
  test -f "$BACKUP_DIR/$file" || { echo "missing backup file: $file" >&2; exit 1; }
done

(
  cd "$BACKUP_DIR"
  grep -qx 'format=dofast-backup-v1' manifest.txt
  sha256sum -c SHA256SUMS
)

compose=(docker compose -f "$COMPOSE_FILE")

# Restore is intentionally target-oriented: it never drops or overwrites the source POSTGRES_DB.
"${compose[@]}" exec -T "$DB_SERVICE" sh -eu -c '
  : "${POSTGRES_USER:?POSTGRES_USER missing in db container}"
  target="$1"
  if psql --username "$POSTGRES_USER" --dbname postgres --tuples-only --no-align \
      --command "SELECT 1 FROM pg_database WHERE datname = '\''${target//\'\'/\'\'\'}'\''" | grep -qx 1; then
    echo "target database already exists: $target" >&2
    exit 1
  fi
  createdb --username "$POSTGRES_USER" "$target"
' sh "$TARGET_DB"

if ! cat "$BACKUP_DIR/postgres.dump" | "${compose[@]}" exec -T "$DB_SERVICE" sh -eu -c '
  : "${POSTGRES_USER:?POSTGRES_USER missing in db container}"
  pg_restore --username "$POSTGRES_USER" --dbname "$1" --no-owner --no-privileges --exit-on-error
' sh "$TARGET_DB"; then
  "${compose[@]}" exec -T "$DB_SERVICE" sh -eu -c '
    : "${POSTGRES_USER:?POSTGRES_USER missing in db container}"
    dropdb --username "$POSTGRES_USER" --if-exists "$1"
  ' sh "$TARGET_DB" || true
  exit 1
fi

"${compose[@]}" exec -T "$API_SERVICE" sh -eu -c '
  target="$1"
  case "$target" in
    /tmp/dofast-restore-*|/var/lib/dofast/restore/*) ;;
    *) echo "unsafe restore attachment target: $target" >&2; exit 1 ;;
  esac
  if [ -e "$target" ] && [ "$(find "$target" -mindepth 1 -print -quit 2>/dev/null)" ]; then
    echo "attachment restore target is not empty: $target" >&2
    exit 1
  fi
  mkdir -p "$target"
' sh "$TARGET_ATTACHMENT_ROOT"

gzip -dc "$BACKUP_DIR/attachments.tar.gz" | "${compose[@]}" exec -T "$API_SERVICE" sh -eu -c '
  target="$1"
  tar -C "$target" -xf -
' sh "$TARGET_ATTACHMENT_ROOT"

printf 'restored database=%s attachments=%s\n' "$TARGET_DB" "$TARGET_ATTACHMENT_ROOT"
