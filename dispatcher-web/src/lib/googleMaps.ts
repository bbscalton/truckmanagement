import { MAPS_API_KEY } from '../firebase'

let mapsPromise: Promise<typeof google> | null = null

export class GoogleMapsLoadError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'GoogleMapsLoadError'
  }
}

export function loadGoogleMaps(): Promise<typeof google> {
  if (typeof google !== 'undefined' && google.maps?.Map) return Promise.resolve(google)
  if (mapsPromise) return mapsPromise
  mapsPromise = new Promise((resolve, reject) => {
    if (!MAPS_API_KEY.trim()) {
      reject(new GoogleMapsLoadError('Google Maps API key is missing. Set VITE_GOOGLE_MAPS_API_KEY before building.'))
      return
    }
    const script = document.createElement('script')
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(MAPS_API_KEY)}&loading=async`
    script.async = true
    script.onload = () => {
      if (typeof google !== 'undefined' && google.maps?.Map) {
        resolve(google)
      } else {
        reject(
          new GoogleMapsLoadError(
            'Maps failed to load. Check API key HTTP referrers for this site (truckmgmt-dev.web.app). Google Maps Platform still requires a linked Cloud billing account for quota even when Maps APIs are enabled.',
          ),
        )
      }
    }
    script.onerror = () =>
      reject(new GoogleMapsLoadError('Failed to load Google Maps script. Check network, API key, and referrer restrictions.'))
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
