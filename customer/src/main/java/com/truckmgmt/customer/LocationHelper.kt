package com.truckmgmt.customer

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLatLng(context: Context): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
            ?: client.lastLocation.await()
        return location?.let { LatLng(it.latitude, it.longitude) }
    }
}
