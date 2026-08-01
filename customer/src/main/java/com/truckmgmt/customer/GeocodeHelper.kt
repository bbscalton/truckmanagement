package com.truckmgmt.customer

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class AddressPoint(
    val address: String,
    val lat: Double,
    val lng: Double,
)

object GeocodeHelper {
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                results?.firstOrNull()?.let { formatAddress(it) }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun searchAddresses(context: Context, query: String, maxResults: Int = 5): List<AddressPoint> =
        withContext(Dispatchers.IO) {
            if (query.length < 3 || !Geocoder.isPresent()) return@withContext emptyList()
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault()).getFromLocationName(query, maxResults)
                    ?: return@withContext emptyList()
                results.mapNotNull { addr ->
                    val lat = addr.latitude
                    val lng = addr.longitude
                    if (lat == 0.0 && lng == 0.0) null
                    else AddressPoint(formatAddress(addr), lat, lng)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun formatAddress(address: android.location.Address): String {
        val parts = (0..address.maxAddressLineIndex)
            .mapNotNull { index -> address.getAddressLine(index)?.takeIf { it.isNotBlank() } }
        if (parts.isNotEmpty()) return parts.joinToString(", ")
        return listOfNotNull(
            address.featureName,
            address.thoroughfare,
            address.locality,
            address.adminArea,
            address.postalCode,
        ).filter { it.isNotBlank() }.joinToString(", ")
    }

    fun latLngString(latLng: LatLng): String =
        String.format(Locale.US, "%.5f, %.5f", latLng.latitude, latLng.longitude)
}
