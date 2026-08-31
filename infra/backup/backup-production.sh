#!/usr/bin/env bash
set -euo pipefail

umask 077

COMPOSE_FILE="${DOFAST_COMPOSE_FILE:-infra/compose/compose.prod.yaml}"
BACKUP_ROOT="${DOFAST_BACKUP_ROOT:?DOFAST_BACKUP_ROOT is required}"
DB_SERVICE="${DOFAST_DB_SERVICE:-db}"
API_SERVICE="${DOFAST_API_SERVICE:-api}"
ATTACHMENT_ROOT="${DOFAST_ATTACHMENT_ROOT:-/var/lib/dofast/attachments}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_ROOT"
work_dir="$(mktemp -d "$BACKUP_ROOT/.dofast-backup-${stamp}.XXXXXX")"
final_dir="$BACKUP_ROOT/dofast-backup-$stamp"
cleanup() { rm -rf "$work_dir"; }
trap cleanup EXIT

compose=(docker compose -f "$COMPOSE_FILE")

"${compose[@]}" exec -T "$DB_SERVICE" sh -eu -c '
  : "${POSTGRES_USER:?POSTGRES_USER missing in db container}"
  : "${POSTGRES_DB:?POSTGRES_DB missing in db container}"
  pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --no-owner --no-privileges
' > "$work_dir/postgres.dump"

test -s "$work_dir/postgres.dump" || { echo "database backup is empty" >&2; exit 1; }

"${compose[@]}" exec -T "$API_SERVICE" sh -eu -c '
  root="$1"
  test -d "$root"
  tar -C "$root" -cf - .
' sh "$ATTACHMENT_ROOT" | gzip -9 > "$work_dir/attachments.tar.gz"

test -s "$work_dir/attachments.tar.gz" || { echo "attachment backup is empty" >&2; exit 1; }

cat > "$work_dir/manifest.txt" <<EOF
format=dofast-backup-v1
created_at_utc=$stamp
postgres_file=postgres.dump
attachments_file=attachments.tar.gz
attachment_root=$ATTACHMENT_ROOT
EOF

(
  cd "$work_dir"
  sha256sum postgres.dump attachments.tar.gz manifest.txt > SHA256SUMS
  sha256sum -c SHA256SUMS >/dev/null
)

chmod -R go-rwx "$work_dir"
mv "$work_dir" "$final_dir"
trap - EXIT
printf '%s\n' "$final_dir"
