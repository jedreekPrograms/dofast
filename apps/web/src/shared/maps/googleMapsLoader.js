let loadPromise = null

export function hasGoogleMapsKey() {
  return Boolean(import.meta.env.VITE_GOOGLE_MAPS_BROWSER_KEY?.trim())
}

export function loadGoogleMaps() {
  if (window.google?.maps?.importLibrary) {
    return Promise.resolve(window.google.maps)
  }
  if (loadPromise) return loadPromise

  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_BROWSER_KEY?.trim()
  if (!apiKey) {
    return Promise.reject(new Error('Google Maps browser key is not configured'))
  }

  loadPromise = new Promise((resolve, reject) => {
    const callbackName = `__dofastGoogleMapsReady_${Date.now()}`
    const script = document.createElement('script')

    window[callbackName] = () => {
      delete window[callbackName]
      resolve(window.google.maps)
    }

    script.async = true
    script.defer = true
    script.onerror = () => {
      delete window[callbackName]
      loadPromise = null
      reject(new Error('Nie udało się załadować Google Maps.'))
    }
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&v=weekly&loading=async&callback=${callbackName}`
    document.head.appendChild(script)
  })

  return loadPromise
}
