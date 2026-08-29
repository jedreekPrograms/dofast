const PROPOSAL_RETURN_PARAM_KEYS = [
  'proposalFunding',
  'proposalId',
  'payment_intent',
  'payment_intent_client_secret',
  'redirect_status',
]

function positiveInteger(value) {
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

export function buildProposalAcceptanceReturnUrl(origin, jobId, proposalId) {
  const normalizedJobId = positiveInteger(jobId)
  const normalizedProposalId = positiveInteger(proposalId)
  if (!normalizedJobId || !normalizedProposalId) {
    throw new TypeError('Valid job and proposal ids are required for the Stripe return URL')
  }

  const url = new URL(`/jobs/${normalizedJobId}`, origin)
  url.searchParams.set('proposalFunding', 'return')
  url.searchParams.set('proposalId', String(normalizedProposalId))
  return url.toString()
}

export function readProposalAcceptanceReturn(href) {
  const url = new URL(href)
  if (url.searchParams.get('proposalFunding') !== 'return') {
    return null
  }

  const proposalId = positiveInteger(url.searchParams.get('proposalId'))
  const redirectStatus = url.searchParams.get('redirect_status')?.trim().toLowerCase() || null
  PROPOSAL_RETURN_PARAM_KEYS.forEach((key) => url.searchParams.delete(key))

  return {
    proposalId,
    redirectStatus,
    sanitizedLocation: `${url.pathname}${url.search}${url.hash}`,
  }
}
