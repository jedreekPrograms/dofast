const STRIPE_SCRIPT_URL = 'https://js.stripe.com/v3/'

export const STRIPE_PUBLISHABLE_KEY = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY?.trim()

let stripeScriptPromise = null

export function loadStripeScript() {
  if (window.Stripe) return Promise.resolve(window.Stripe)
  if (stripeScriptPromise) return stripeScriptPromise

  stripeScriptPromise = new Promise((resolve, reject) => {
    let script = document.querySelector('script[data-dofast-stripe]')

    const handleLoad = () => {
      if (window.Stripe) {
        resolve(window.Stripe)
      } else {
        stripeScriptPromise = null
        reject(new Error('Stripe.js załadował się bez globalnego klienta Stripe.'))
      }
    }

    const handleError = () => {
      stripeScriptPromise = null
      reject(new Error('Nie udało się załadować Stripe.js.'))
    }

    if (!script) {
      script = document.createElement('script')
      script.src = STRIPE_SCRIPT_URL
      script.async = true
      script.dataset.dofastStripe = 'true'
      document.head.appendChild(script)
    }

    script.addEventListener('load', handleLoad, { once: true })
    script.addEventListener('error', handleError, { once: true })
  })

  return stripeScriptPromise
}
