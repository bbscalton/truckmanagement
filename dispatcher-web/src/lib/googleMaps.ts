import { MAPS_API_KEY } from '../firebase'

let mapsPromise: Promise<typeof google> | null = null

export function loadGoogleMaps(): Promise<typeof google> {
  if (typeof google !== 'undefined' && google.maps) return Promise.resolve(google)
  if (mapsPromise) return mapsPromise
  mapsPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(MAPS_API_KEY)}`
    script.async = true
    script.onload = () => resolve(google)
    script.onerror = () => reject(new Error('Failed to load Google Maps'))
    document.head.appendChild(script)
  })
  return mapsPromise
}

export function createSatelliteMap(el: HTMLElement, center = { lat: 18.0, lng: -76.8 }): google.maps.Map {
  return new google.maps.Map(el, {
    center,
    zoom: 12,
    mapTypeId: google.maps.MapTypeId.SATELLITE,
    streetViewControl: false,
  })
}
