# Backup and restore

## Scope

doFast has two durable data sets in the single-host production baseline:

1. PostgreSQL/PostGIS data in `postgres_data`;
2. encrypted attachment objects in `attachment_data`.

`infra/backup/backup-production.sh` creates one atomic backup directory containing both data sets, a versioned manifest and SHA-256 checksums. `infra/backup/restore-production.sh` restores the database into a **new database name** and attachments into a dedicated restore directory. The restore command intentionally refuses to overwrite the active production database or the live attachment root.

These scripts make backup creation and restore drills reproducible. They do not by themselves provide off-host durability. Production operations must copy completed backup directories to a separate failure domain with access control, retention and monitoring.

## Backup bundle

A successful bundle contains:

- `postgres.dump` — PostgreSQL custom-format dump produced by the database container;
- `attachments.tar.gz` — encrypted attachment files exactly as stored by the API;
- `manifest.txt` — bundle format/version and creation timestamp;
- `SHA256SUMS` — integrity hashes for every payload and the manifest.

The script writes into a private temporary directory and renames it only after all payloads are non-empty and checksum verification passes. A failed run therefore does not look like a complete backup.

The host backup root is created with restrictive permissions through `umask 077`. Operators must still protect the destination filesystem and any off-host copy.

## Create a backup

Run from the repository root with the production environment exported exactly as for the deployed Compose stack:

```bash
export DOFAST_BACKUP_ROOT=/srv/dofast/backups
export DOFAST_COMPOSE_FILE=infra/compose/compose.prod.yaml
bash infra/backup/backup-production.sh
```

The command prints the final backup directory on success.

After creation:

1. copy the whole directory off-host without modifying its contents;
2. independently protect the attachment encryption key and other required deployment secrets in a secret manager/backed-up credential store;
3. record/monitor backup age and copy success;
4. periodically exercise the restore procedure below.

Do **not** place database credentials, Stripe secrets, JWT keys or the attachment encryption key inside the backup bundle.

## Restore drill

A restore is deliberately target-oriented. The source production database is never dropped by the script. Choose a new database and an isolated attachment path:

```bash
export DOFAST_BACKUP_DIR=/srv/dofast/backups/dofast-backup-YYYYMMDDTHHMMSSZ
export DOFAST_COMPOSE_FILE=infra/compose/compose.prod.yaml
export DOFAST_RESTORE_DB=dofast_restore_drill_20260831
export DOFAST_RESTORE_ATTACHMENT_ROOT=/var/lib/dofast/restore/20260831
export DOFAST_RESTORE_CONFIRM=RESTORE_DOFAST_BACKUP
bash infra/backup/restore-production.sh
```

Before restoring, the script verifies `format=dofast-backup-v1` and all SHA-256 checksums. It rejects unsafe database names, refuses the active `POSTGRES_DB`, fails when the target database already exists, and only permits attachment restore paths under `/var/lib/dofast/restore/` or `/tmp/dofast-restore-*`.

If `pg_restore` fails, the newly created target database is dropped best-effort so an incomplete database is not mistaken for a valid restore.

## Restore verification

A real drill should verify at least:

- PostgreSQL accepts connections to the restored target;
- the `postgis` extension is present;
- representative user/job/ledger rows match expected counts or sampled identifiers;
- encrypted attachment file counts/checksums are plausible;
- a temporary API instance configured with the restored database, restored attachment directory and the **original attachment encryption key** can read representative attachment objects;
- Flyway reports no unexpected migration divergence;
- financial ledger reconciliation and application health checks pass before any disaster-recovery cutover is considered.

Never promote a restore merely because `pg_restore` exited successfully.

## CI restore drill

`.github/scripts/backup-restore-smoke.sh` runs inside the main container/runtime CI job. It:

1. seeds a database marker and attachment marker;
2. creates a real bundle through `backup-production.sh`;
3. verifies the bundle checksums;
4. restores into a new PostgreSQL database and isolated attachment directory;
5. verifies the database marker, attachment bytes and PostGIS extension.

This protects the executable backup contract from image, Compose, PostgreSQL and filesystem drift.

## Disaster-recovery boundary

The current repository now provides reproducible backup/restore tooling and a continuously tested restore path, but production launch still requires an operator/environment decision for:

- off-host destination and encryption-at-rest controls;
- retention schedule and deletion policy;
- backup success/age alerting;
- protection and recovery of attachment encryption keys and deployment secrets;
- documented RPO/RTO targets;
- periodic human-reviewed restore drills in production-like infrastructure.

A Docker volume is not a backup, and a backup that has never been restored is not considered verified.
