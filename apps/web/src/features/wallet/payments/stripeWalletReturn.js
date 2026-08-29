const STRIPE_RETURN_PARAM_KEYS = [
  'topup',
  'payment_intent',
  'payment_intent_client_secret',
  'redirect_status',
]

export function readWalletStripeReturn(href) {
  const url = new URL(href)
  if (url.searchParams.get('topup') !== 'return') {
    return null
  }

  const clientSecret = url.searchParams.get('payment_intent_client_secret')?.trim() || null
  STRIPE_RETURN_PARAM_KEYS.forEach((key) => url.searchParams.delete(key))

  return {
    clientSecret,
    sanitizedLocation: `${url.pathname}${url.search}${url.hash}`,
  }
}
