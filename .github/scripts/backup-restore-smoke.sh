#!/usr/bin/env bash
set -euo pipefail

backup_root="$(mktemp -d)"
restore_root="/tmp/dofast-restore-smoke-$$"
restore_db="dofast_restore_smoke_$$"
cleanup() {
  docker compose exec -T db sh -c 'dropdb --username "$POSTGRES_USER" --if-exists "$1"' sh "$restore_db" >/dev/null 2>&1 || true
  docker compose exec -T api rm -rf "$restore_root" >/dev/null 2>&1 || true
  rm -rf "$backup_root"
}
trap cleanup EXIT

marker="backup-smoke-$(date +%s)-$$"
docker compose exec -T db sh -eu -c '
  psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
    --command "CREATE TABLE IF NOT EXISTS backup_restore_smoke (marker text PRIMARY KEY);" \
    --command "TRUNCATE backup_restore_smoke;" \
    --command "INSERT INTO backup_restore_smoke(marker) VALUES ('\''$1'\'');"
' sh "$marker"

docker compose exec -T api sh -eu -c '
  mkdir -p /var/lib/dofast/attachments/backup-smoke
  printf "%s" "$1" > /var/lib/dofast/attachments/backup-smoke/marker.txt
' sh "$marker"

export DOFAST_COMPOSE_FILE=compose.yaml
export DOFAST_BACKUP_ROOT="$backup_root"
backup_dir="$(bash infra/backup/backup-production.sh)"

test -f "$backup_dir/SHA256SUMS"
(
  cd "$backup_dir"
  sha256sum -c SHA256SUMS
)

export DOFAST_BACKUP_DIR="$backup_dir"
export DOFAST_RESTORE_DB="$restore_db"
export DOFAST_RESTORE_ATTACHMENT_ROOT="$restore_root"
export DOFAST_RESTORE_CONFIRM=RESTORE_DOFAST_BACKUP
bash infra/backup/restore-production.sh

restored_marker="$(docker compose exec -T db sh -eu -c '
  psql --username "$POSTGRES_USER" --dbname "$1" --tuples-only --no-align \
    --command "SELECT marker FROM backup_restore_smoke LIMIT 1;"
' sh "$restore_db" | tr -d '\r')"

test "$restored_marker" = "$marker"

restored_attachment="$(docker compose exec -T api cat "$restore_root/backup-smoke/marker.txt" | tr -d '\r')"
test "$restored_attachment" = "$marker"

docker compose exec -T db sh -eu -c '
  test "$(psql --username "$POSTGRES_USER" --dbname "$1" --tuples-only --no-align --command "SELECT extname FROM pg_extension WHERE extname = '\''postgis'\'';")" = postgis
' sh "$restore_db"

echo "Backup/restore smoke passed"
