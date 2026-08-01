import L from 'leaflet'

export type LatLng = { lat: number; lng: number }

export type DriverMarker = {
  lat: number
  lng: number
  title: string
  online: boolean
}

export class FleetMapLoadError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'FleetMapLoadError'
  }
}

const ESRI_IMAGERY = L.tileLayer(
  'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
  {
    attribution:
      'Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community',
    maxZoom: 19,
  },
)

const OSM_STREET = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  maxZoom: 19,
})

export type FleetMapHandle = {
  map: L.Map
  setCenter: (lat: number, lng: number, zoom?: number) => void
  setMarkers: (drivers: DriverMarker[]) => void
  setStopMarkers: (stops: LatLng[]) => void
  drawPolyline: (path: LatLng[]) => void
  clearLayers: () => void
  destroy: () => void
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

export function initFleetMap(el: HTMLElement, center: LatLng = { lat: 18.0, lng: -76.8 }): FleetMapHandle {
  if (!el) {
    throw new FleetMapLoadError('Map container element is missing.')
  }

  let map: L.Map
  try {
    map = L.map(el, {
      center: [center.lat, center.lng],
      zoom: 12,
      layers: [ESRI_IMAGERY],
    })
  } catch (e) {
    throw new FleetMapLoadError(e instanceof Error ? e.message : 'Failed to initialize map.')
  }

  L.control
    .layers(
      { Satellite: ESRI_IMAGERY, Street: OSM_STREET },
      undefined,
      { position: 'topright' },
    )
    .addTo(map)

  const markersLayer = L.layerGroup().addTo(map)
  let polyline: L.Polyline | null = null

  return {
    map,

    setCenter(lat: number, lng: number, zoom?: number) {
      map.setView([lat, lng], zoom ?? map.getZoom())
    },

    setMarkers(drivers: DriverMarker[]) {
      markersLayer.clearLayers()
      for (const d of drivers) {
        const marker = L.marker([d.lat, d.lng], {
          icon: L.divIcon({
            className: 'fleet-marker',
            html: `<div class="fleet-marker-pin ${d.online ? 'online' : 'offline'}"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 17h13v-5H3v5zm13-5h3l2 3v5h-5v-5z"/></svg></div>`,
            iconSize: [32, 32],
            iconAnchor: [16, 16],
          }),
        })
        marker.bindTooltip(escapeHtml(d.title))
        markersLayer.addLayer(marker)
      }
    },

    setStopMarkers(stops: LatLng[]) {
      markersLayer.clearLayers()
      for (const s of stops) {
        const marker = L.circleMarker([s.lat, s.lng], {
          radius: 8,
          color: '#922b21',
          fillColor: '#e74c3c',
          fillOpacity: 0.9,
          weight: 2,
        })
        marker.bindTooltip('Stop')
        markersLayer.addLayer(marker)
      }
    },

    drawPolyline(path: LatLng[]) {
      if (polyline) {
        map.removeLayer(polyline)
        polyline = null
      }
      if (!path.length) return
      polyline = L.polyline(
        path.map((p) => [p.lat, p.lng] as L.LatLngExpression),
        { color: '#E67E22', weight: 5 },
      ).addTo(map)
    },

    clearLayers() {
      markersLayer.clearLayers()
      if (polyline) {
        map.removeLayer(polyline)
        polyline = null
      }
    },

    destroy() {
      map.remove()
    },
  }
}
