package com.truckmgmt.customer

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

object PlacesHelper {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context, apiKey: String): Boolean {
        if (initialized || Places.isInitialized()) {
            initialized = true
            return true
        }
        if (apiKey.isBlank() || apiKey == "YOUR_MAPS_API_KEY") return false
        return try {
            Places.initialize(context.applicationContext, apiKey)
            initialized = true
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildAutocompleteIntent(context: Context) =
        Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, listOf(Place.Field.LAT_LNG, Place.Field.ADDRESS))
            .build(context)
}
