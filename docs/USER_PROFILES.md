# Public user profiles

## Purpose

Public profiles provide a lightweight marketplace trust card for requesters and workers. They combine user-controlled presentation fields and declared service specializations with existing transactional trust signals without exposing private account or job data.

`GET /users/{id}/profile` returns:

- public nickname,
- optional public bio,
- optional public location label,
- account creation time (`memberSince`),
- up to 10 active leaf service specializations,
- average rating and review count,
- completed-job counts as requester and worker,
- identity-verification badge state.

The endpoint does not return email addresses, password/authentication information, wallet or payment data, exact job locations, routes, live tracking, private verification documents or moderation records.

## Editing profile fields

Authenticated users edit their own profile through `PATCH /users/me/profile`. The request supports:

- `nickname` — required, 3–80 characters,
- `bio` — optional, maximum 600 characters,
- `publicLocation` — optional, maximum 120 characters.

Optional values are trimmed server-side and blank values are stored as `NULL`, so clearing a field removes it from the public profile. Flyway `V32__user_public_profiles.sql` adds the two optional columns and database checks that prevent persisted blank values.

## Service specializations

Service specializations intentionally have a separate owner-only update contract so an older profile client cannot accidentally clear them while updating nickname or bio:

- `GET /users/me/service-categories` — current authenticated user's active specializations,
- `PUT /users/me/service-categories` — replace the selection with `categoryIds`.

The selection is limited to 10 categories. Only active, concrete leaf service categories are accepted; parent catalog groups cannot be selected. The backend validates IDs against the server-side catalog rather than trusting client-provided names or fulfillment modes.

Flyway `V33__user_service_categories.sql` stores the relation in `user_service_categories` with foreign keys to `users` and `job_categories`, `ON DELETE CASCADE`, a unique `(user_id, category_id)` constraint and an index for category-side lookups. Replacement is implemented as a transactional diff, preserving unchanged rows and avoiding delete/reinsert conflicts.

If a selected catalog category is later deactivated, it is no longer returned as a public specialization. A later profile update can remove the stale relation normally. Specializations are declarative profile data only: selecting one does not automatically accept matching jobs, subscribe to alerts, grant location access or imply current availability.

## Inline marketplace trust cards

The web client reuses the same public profile contract in decision-heavy surfaces instead of showing only opaque numeric user IDs. Job details render requester and worker trust cards, and an open job therefore exposes the requester's public reputation before another user accepts it. The selected chat conversation also renders a compact counterpart trust card above the message history.

Inline cards show only public profile data: verification state, rating/review count, completed-job total, optional public location, declared specializations and a link to the full profile. The fuller job-details variant can also show the public bio and account age. Profile requests are deduplicated and cached for the current browser session so moving between conversations does not create repeated reads for the same user.

Trust-card loading is deliberately fail-open for the surrounding workflow. If the public profile request fails, job lifecycle controls and chat history/composer continue to work and the card degrades to a simple user reference plus a profile link. A presentation failure therefore cannot block completion, disputes, cancellation, messaging or escrow-related actions.

## Location privacy

`publicLocation` is deliberately a free-form public label such as `Wrocław i okolice`. It is not geocoded and is not linked to the PostGIS coordinates used for job discovery, exact participant-only locations, routes or live tracking. Users therefore choose the granularity they want to publish.

The profile editor explicitly labels bio, public location and service specializations as public information. Exact addresses remain part of the dedicated job/location privacy model and must not be copied from private job fields into a public profile automatically.

## Trust data

`memberSince`, rating aggregates, completed-job counts and identity-verification state are derived server-side. Clients cannot edit these values. Reviews remain the underlying accountability record; profile presentation fields and declared service categories do not affect review eligibility, escrow, job lifecycle or verification status.
