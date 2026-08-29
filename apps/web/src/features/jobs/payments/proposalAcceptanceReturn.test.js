import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildProposalAcceptanceReturnUrl,
  readProposalAcceptanceReturn,
} from './proposalAcceptanceReturn.js'

test('proposal acceptance uses a dedicated recoverable Stripe return URL', () => {
  assert.equal(
    buildProposalAcceptanceReturnUrl('https://dofast.example', 41, 73),
    'https://dofast.example/jobs/41/proposal-payment-return?proposalFunding=return&proposalId=73',
  )
})

test('proposal Stripe return keeps non-secret recovery context and removes Stripe query data', () => {
  const result = readProposalAcceptanceReturn(
    'https://dofast.example/jobs/41/proposal-payment-return?proposalFunding=return&proposalId=73&payment_intent=pi_123&payment_intent_client_secret=pi_123_secret_abc&redirect_status=processing&tab=proposals#offers',
  )

  assert.equal(result.proposalId, 73)
  assert.equal(result.redirectStatus, 'processing')
  assert.equal(
    result.sanitizedLocation,
    '/jobs/41/proposal-payment-return?proposalFunding=return&proposalId=73&tab=proposals#offers',
  )
  assert.equal(result.sanitizedLocation.includes('payment_intent'), false)
  assert.equal(result.sanitizedLocation.includes('secret'), false)
})

test('recognized return is still sanitized when proposal context is invalid', () => {
  const result = readProposalAcceptanceReturn(
    'https://dofast.example/jobs/41/proposal-payment-return?proposalFunding=return&proposalId=not-a-number&payment_intent_client_secret=pi_secret',
  )

  assert.equal(result.proposalId, null)
  assert.equal(
    result.sanitizedLocation,
    '/jobs/41/proposal-payment-return?proposalFunding=return&proposalId=not-a-number',
  )
})

test('Stripe-like parameters are ignored without the proposal funding marker', () => {
  assert.equal(
    readProposalAcceptanceReturn(
      'https://dofast.example/jobs/41/proposal-payment-return?proposalId=73&payment_intent_client_secret=pi_unrelated_secret',
    ),
    null,
  )
})

test('return URL builder rejects invalid marketplace identifiers', () => {
  assert.throws(
    () => buildProposalAcceptanceReturnUrl('https://dofast.example', 0, 73),
    TypeError,
  )
  assert.throws(
    () => buildProposalAcceptanceReturnUrl('https://dofast.example', 41, -1),
    TypeError,
  )
})
