# Secure job attachments

## Purpose

Job attachments support practical task material without forcing every detail into free-form text. Typical examples are a shopping-list photo, product photo, PDF instruction, receipt reference or a temporary loyalty-card image used by the selected courier.

Attachments are deliberately separate from chat, exact location, payments and moderation evidence. Uploading a file never changes job status, escrow, assignment or location permissions.

## Visibility classes

The requester chooses one of three server-enforced visibility classes for every file:

- `JOB_VIEWERS` — visible to authenticated users who are allowed to view the open job detail. Once a worker is assigned, access closes to outsiders and remains only for the job participants. Use this for material a candidate may legitimately need before deciding whether to take or propose for a job, such as a shopping list or product photo.
- `PARTICIPANTS` — hidden before assignment. The requester and assigned worker can read it after worker selection, including later historical lifecycle states. Use this for normal transaction material that should stay between the two sides.
- `EXECUTION_SECRET` — the requester can always read or revoke it; the assigned worker can read it only while the job is exactly `IN_PROGRESS`. Access disappears immediately on completion request, dispute, completion or cancellation. This is the appropriate class for a temporary loyalty-card image or short-lived pickup credential.

A block relation also hides `JOB_VIEWERS` attachments from a non-participant, matching the existing job-detail visibility policy. Hidden attachments return the same not-found response as nonexistent attachments so their existence is not disclosed.

Do not use attachments for payment-card data, passwords, identity documents or other secrets that are not necessary to perform the task. A loyalty-program barcode is an execution aid, not a substitute for payment credentials.

## Upload and deletion lifecycle

Only the job creator can upload files. Upload is allowed while the job is `OPEN` or `IN_PROGRESS`; later lifecycle states are frozen so new material cannot silently rewrite completed/disputed evidence.

Regular `JOB_VIEWERS` and `PARTICIPANTS` files become immutable after a worker has been selected. `EXECUTION_SECRET` is intentionally different: the creator may delete it at any time, including during an active job, to revoke the worker's access immediately.

Deleted rows are tombstoned in PostgreSQL first. The encrypted object is removed only after the database transaction commits. Conversely, a newly stored object is registered for cleanup if its database transaction rolls back. This prevents normal transaction failures from leaving metadata pointing at a missing file or retaining a failed upload indefinitely.

## File safety

Current limits and accepted formats:

- maximum 10 MiB per file;
- maximum 12 active files per job by default;
- JPEG, PNG, WebP and PDF only;
- SVG, HTML, executables and other active/unknown formats are rejected.

The backend does not trust the browser-supplied MIME type or filename extension. It reads a bounded byte stream, checks the file signature, assigns a canonical media type/extension, strips path/control characters from the display filename and calculates SHA-256 metadata server-side.

Downloads are served with `Content-Disposition: attachment`, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

## Storage and encryption

Binary bytes are not stored in PostgreSQL. PostgreSQL keeps only authorization/audit metadata and an opaque random storage key.

The current adapter stores objects in a private filesystem root (`ATTACHMENT_STORAGE_ROOT`) backed by a dedicated Compose volume. Every object is encrypted before it reaches disk using AES-256-GCM with a fresh 96-bit nonce. The opaque storage key is authenticated as additional data, so ciphertext cannot be moved under another key and still decrypt successfully.

`ATTACHMENT_ENCRYPTION_KEY_BASE64` must decode to exactly 32 bytes. The repository default is explicitly local/CI-only. Production must supply a new high-entropy key through secret management; it must never be exposed to the Vite build or API responses.

The domain depends on the `AttachmentStorage` interface rather than filesystem paths. A future S3/MinIO implementation can replace the local adapter without changing attachment metadata, visibility rules or REST endpoints.

## REST API

All endpoints are authenticated:

- `POST /jobs/{jobId}/attachments` — multipart upload with `visibility` plus `file`; creator only;
- `GET /jobs/{jobId}/attachments` — returns only metadata currently visible to the caller;
- `GET /jobs/{jobId}/attachments/{attachmentId}/content` — authorized decrypted download;
- `DELETE /jobs/{jobId}/attachments/{attachmentId}` — creator deletion under lifecycle rules.

Responses never include storage paths, storage keys, encryption material or SHA-256 internals.

## Schema and runtime validation

Flyway `V36__job_attachments.sql` creates metadata, visibility/size/hash checks and active-job indexes. The application and database hard limits both cap a file at 10 MiB.

Focused tests cover magic-byte validation, filename normalization, count/size limits, encrypted-at-rest round trips, failure with a wrong AES key, root-path confinement and lifecycle access rules. The container runtime smoke additionally verifies multipart upload, metadata privacy, encrypted bytes on the mounted volume, pre/post-assignment access, execution-secret revocation and deletion behavior.
