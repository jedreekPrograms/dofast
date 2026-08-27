# Saved-search radius web flow

The web client supports the private radius criteria introduced for saved-search alerts.

## Creation flow

Authenticated users can optionally enable `Ogranicz alerty do wybranej okolicy` while saving discovery filters. The client reuses the existing `LocationMapPicker` to select a center point and sends only:

- `latitude`;
- `longitude`;
- `radiusKm` from 1 to 100.

The address/public labels used by the map picker are not included in the saved-search request. This keeps saved-search matching independent from private address text while still allowing the user to select a point with autocomplete, a map click or browser geolocation.

The radius can be the only saved-search criterion, or it can be combined with query, category and price filters. Invalid/incomplete coordinates or a radius outside the backend contract prevent submission in the web client and are still validated by the API.

## Management flow

`/saved-searches` shows the configured radius without rendering or exposing the stored coordinates. The normal `Pokaż wyniki` action continues to rebuild only the public discovery URL; private radius criteria are used for alert matching and are not copied into shareable query parameters.

Saved-search updates are replace semantics. Alert enable/disable therefore resends `latitude`, `longitude` and `radiusKm` together with the existing public filters. This prevents a preference-only toggle from accidentally clearing the private radius criterion.

## Privacy boundary

- private center coordinates are available only through the authenticated saved-search API to their owner;
- exact address labels from the map picker are never persisted as part of a saved search;
- notification bodies contain public job information only;
- participant-only job coordinates, route geometry and live tracking are not used as saved-search center data;
- public discovery URLs never contain the private saved-search center.

## Verification

Frontend CI must run lint and production build for this flow. Backend validation, PostGIS persistence and matcher radius behavior remain covered by the API tests introduced with the radius backend slice.
