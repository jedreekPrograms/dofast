export function decodeGooglePolyline(encoded) {
  if (!encoded) return []

  const points = []
  let index = 0
  let latitude = 0
  let longitude = 0

  while (index < encoded.length) {
    const latResult = decodeValue(encoded, index)
    index = latResult.nextIndex
    latitude += latResult.value

    const lngResult = decodeValue(encoded, index)
    index = lngResult.nextIndex
    longitude += lngResult.value

    points.push({ lat: latitude / 1e5, lng: longitude / 1e5 })
  }

  return points
}

function decodeValue(encoded, startIndex) {
  let result = 0
  let shift = 0
  let index = startIndex
  let byte

  do {
    byte = encoded.charCodeAt(index++) - 63
    result |= (byte & 0x1f) << shift
    shift += 5
  } while (byte >= 0x20 && index < encoded.length)

  const value = (result & 1) ? ~(result >> 1) : (result >> 1)
  return { value, nextIndex: index }
}
