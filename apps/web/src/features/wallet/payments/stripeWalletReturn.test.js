import assert from 'node:assert/strict'
import test from 'node:test'

import { readWalletStripeReturn } from './stripeWalletReturn.js'

test('wallet Stripe return is recognized and sensitive query params are removed', () => {
  const result = readWalletStripeReturn(
    'https://dofast.example/wallet?topup=return&payment_intent=pi_123&payment_intent_client_secret=pi_123_secret_abc&redirect_status=succeeded&tab=history#balance',
  )

  assert.equal(result.clientSecret, 'pi_123_secret_abc')
  assert.equal(result.sanitizedLocation, '/wallet?tab=history#balance')
  assert.equal(result.sanitizedLocation.includes('payment_intent'), false)
  assert.equal(result.sanitizedLocation.includes('secret'), false)
})

test('Stripe-like query params are ignored without the wallet return marker', () => {
  const result = readWalletStripeReturn(
    'https://dofast.example/wallet?payment_intent_client_secret=pi_unrelated_secret&redirect_status=succeeded',
  )

  assert.equal(result, null)
})

test('recognized wallet return is sanitized even when Stripe omits the client secret', () => {
  const result = readWalletStripeReturn(
    'https://dofast.example/wallet?topup=return&payment_intent=pi_123&redirect_status=failed&tab=balance',
  )

  assert.equal(result.clientSecret, null)
  assert.equal(result.sanitizedLocation, '/wallet?tab=balance')
})
