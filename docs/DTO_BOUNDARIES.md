# HTTP and STOMP DTO boundaries

This document is the reviewed mass-assignment and sensitive-field contract for doFast transport inputs and HTTP outputs. It complements the endpoint ownership rules in [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md).

## Inbound policy

- REST JSON bodies and STOMP payloads bind only to dedicated request DTOs. Persistence entities, generic maps, `Object` and JSON tree types are not transport contracts.
- Every typed payload is validated with `@Valid`. The sole raw-body exception is the Stripe webhook string, because its exact bytes must be verified against `Stripe-Signature` before event parsing.
- The complete set of direct and nested request DTO fields is snapshotted by `HttpDtoBoundaryPolicyTest`. Adding a type, field, alias, ignored field or any-setter requires an explicit security review and allowlist update.
- Unknown JSON properties fail with `400 Bad Request`; they are never silently ignored. A client therefore cannot smuggle or mistakenly believe it changed fields such as `role`, `balance`, `createdById`, `takenById`, `emailVerified`, provider identifiers or optimistic-lock versions.
- Actor identity always comes from the authenticated principal. Resource identity comes from the scoped path/body identifier and is re-authorized in the service/repository boundary. DTO values are copied into domain objects field by field; no generic bean/entity copier is used.

The audited surface contains 39 bound payload parameters, 38 dedicated direct-or-nested request DTO types and one signed raw webhook body. Two fields named `status` remain intentionally writable only in dedicated administrator requests: account reactivation/status management and report moderation. Other privileged administrator decisions use narrowly typed `decision`, `resolution` or `action` enums on `/admin/**` endpoints.

## Outbound policy

Controller responses use explicit projections and never return a JPA entity directly or nested inside another project response type. Sensitive projections remain governed by their endpoint rules:

- public job, profile and review responses are sanitized;
- exact location, route, attachment, chat and historical commercial data are participant-scoped;
- email, verification, moderation and finance projections are actor/admin-scoped;
- access tokens and payment client secrets appear only in the dedicated authentication/payment responses that create them.

`HttpDtoBoundaryPolicyTest` recursively checks controller return types for persistence models so a later convenience refactor cannot expose an entity graph accidentally.

## Change rule

When adding a REST body, STOMP payload or nested request DTO, keep the contract typed and validated, add only client-owned fields, update the executable field snapshot and document any exceptional privileged input. When adding an HTTP response, return a purpose-built projection and keep resource authorization at the service boundary.
