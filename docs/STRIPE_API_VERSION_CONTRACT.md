# Stripe API version contract

## Current reviewed contract

- `stripe-java`: `33.3.0`
- reviewed Stripe API version: `2026-07-29.dahlia`
- source of truth in doFast: `StripeApiVersionContract.REVIEWED_API_VERSION`

Stripe Java pins its generated request/response model to a concrete Stripe API version and sends that version on outgoing API requests. The doFast integration must therefore treat an SDK change that changes `Stripe.API_VERSION` as a payment-contract migration, not as a routine dependency bump.

## Fail-closed behavior

`StripeConfig` verifies `Stripe.API_VERSION` during application startup before configuring the API key. If the SDK is pinned to a different API version, the application refuses to start.

For signed webhook events that can mutate financial state, `StripeWebhookController` requires `event.api_version` to equal the reviewed API version before any settlement service is invoked. A mismatch returns HTTP 500 so Stripe can retry after the deployment or webhook endpoint configuration is corrected. Unhandled event types remain ignored with HTTP 200.

Do not use `RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride` or unsafe event deserialization to bypass this contract. A request version that does not match the SDK model can silently lose or reinterpret fields.

## Controlled SDK/API upgrade procedure

1. Read the Stripe Java release notes and Stripe API changelog for the target version.
2. Identify request, response, error, enum/default and webhook schema changes affecting PaymentIntents, refunds, disputes, Connect payouts/transfers and account onboarding.
3. Update the SDK in a dedicated PR.
4. Keep `REVIEWED_API_VERSION` unchanged initially. The version-contract test/startup guard should fail if the SDK changed its pinned API version.
5. Update payment/provider code and webhook fixtures for the new schema as needed.
6. Configure/test the Stripe webhook endpoint against the target API version. Webhook event data is rendered using the event's own immutable `api_version`.
7. Intentionally update `REVIEWED_API_VERSION` only after the migration has been reviewed.
8. Require exact-head green results for Maven/API tests, Stripe webhook rollback/retry smokes, payments ledger, worker payout, platform-fee settlement, job-publication payment, CodeQL, container security and the full runtime smoke suite.
9. Merge only if `master` is unchanged and the PR branch is not behind.

A Stripe SDK upgrade must never be made green by forcing a Stripe-Version override or using unsafe deserialization.
