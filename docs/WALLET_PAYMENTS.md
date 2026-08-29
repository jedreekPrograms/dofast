# Wallet payments

Wallet top-ups use Stripe PaymentIntents and the Payment Element. doFast enables Stripe automatic payment methods, including redirect-based methods that are eligible for the account and currency.

## Creation and idempotency

- `StripePaymentService` creates PLN PaymentIntents for amounts from 1.00 to 10,000.00 PLN.
- Every intent carries `userId`, `purpose=TOP_UP`, and `topUpRequestId` metadata.
- Stripe creation uses the idempotency key `dofast:topup:{userId}:{topUpRequestId}`.
- Redirect methods are not disabled at PaymentIntent creation. The web flow provides a wallet-specific Stripe `return_url`.

## Browser return flow

The Payment Element returns redirect-based methods to `/wallet?topup=return`. Stripe can append `payment_intent`, `payment_intent_client_secret`, and `redirect_status` query parameters.

The wallet UI treats `topup=return` as the required flow marker. On a recognized return it reads the client secret into memory and synchronously removes all Stripe return parameters from the browser URL with `history.replaceState` before loading Stripe or making any asynchronous status request. Unrelated query parameters and the URL hash are preserved.

The returned browser status is informational only. `succeeded`, `processing`, or `redirect_status` in the URL never credits the wallet and never acts as settlement authority.

## Gateway privacy

Stripe redirect parameters necessarily arrive on the first browser request before React can scrub the address bar. The production nginx access log therefore records `$uri` rather than the full request target and deliberately omits query strings and referrers. This prevents Stripe client secrets and other query data from being persisted in the web container access log.

The web gateway also sends `Referrer-Policy: strict-origin`, so subsequent browser requests receive only the origin as referrer rather than a path or query string. A container smoke test requests a wallet return URL containing a synthetic secret and fails if that marker appears in nginx logs.

## Settlement authority

Wallet balance is credited only by the server-side Stripe webhook path after signature verification and PaymentIntent validation. Settlement verifies the final `succeeded` state, PLN currency, supported amount range, user metadata, and `TOP_UP` purpose before claiming the payment transaction and crediting the wallet ledger.

The payment transaction claim plus wallet ledger reference keep webhook retries idempotent. A reused Stripe event or PaymentIntent with conflicting stored data is rejected instead of crediting again.

## Source-of-funds policy

A successful Stripe top-up creates a `STRIPE_PAYMENT` funding lot keyed by the exact PaymentIntent id. Card-funded value is spendable inside doFast but is **not withdrawable** through worker payouts. This prevents a wallet top-up from becoming a cash-out mechanism.

Ordinary internal spending consumes non-withdrawable value before worker earnings whenever possible, preserving legitimate `EARNED_JOB` value for later payout.

Original-method Stripe refunds are source-specific. A refund can reserve only the remaining value from the exact PaymentIntent being refunded. Money from another PaymentIntent, job earnings, legacy balance, or a platform adjustment cannot substitute for an exhausted original payment.

If a provider refund fails, the wallet restoration follows the funding movement created by that refund reserve and returns the value to the same `STRIPE_PAYMENT` lot. It does not create a new generic or withdrawable balance.

The canonical accounting model and operator rules are documented in `docs/WALLET_SOURCE_OF_FUNDS.md`.

## Operational checks

Changes to this flow must keep the API Maven verification, frontend Node tests/lint/build, container/runtime smoke, dedicated web-container privacy smoke, and payments-ledger smoke green. Source-of-funds changes additionally require Flyway V49, focused funding-allocation tests, payout smoke, Stripe refund/chargeback smoke, and publication-payment smoke to remain green on the exact head SHA.
