# Public user profiles

## Purpose

Public profiles provide a lightweight marketplace trust card for requesters and workers. They combine user-controlled presentation fields with existing transactional trust signals without exposing private account or job data.

`GET /users/{id}/profile` returns:

- public nickname,
- optional public bio,
- optional public location label,
- account creation time (`memberSince`),
- average rating and review count,
- completed-job counts as requester and worker,
- identity-verification badge state.

The endpoint does not return email addresses, password/authentication information, wallet or payment data, exact job locations, routes, live tracking, private verification documents or moderation records.

## Editing

Authenticated users edit their own profile through `PATCH /users/me/profile`. The request supports:

- `nickname` — required, 3–80 characters,
- `bio` — optional, maximum 600 characters,
- `publicLocation` — optional, maximum 120 characters.

Optional values are trimmed server-side and blank values are stored as `NULL`, so clearing a field removes it from the public profile. Flyway `V32__user_public_profiles.sql` adds the two optional columns and database checks that prevent persisted blank values.

## Location privacy

`publicLocation` is deliberately a free-form public label such as `Wrocław i okolice`. It is not geocoded and is not linked to the PostGIS coordinates used for job discovery, exact participant-only locations, routes or live tracking. Users therefore choose the granularity they want to publish.

The profile editor explicitly labels bio and public location as public information. Exact addresses remain part of the dedicated job/location privacy model and must not be copied from private job fields into a public profile automatically.

## Trust data

`memberSince`, rating aggregates, completed-job counts and identity-verification state are derived server-side. Clients cannot edit these values. Reviews remain the underlying accountability record; profile presentation fields do not affect review eligibility, escrow, job lifecycle or verification status.
