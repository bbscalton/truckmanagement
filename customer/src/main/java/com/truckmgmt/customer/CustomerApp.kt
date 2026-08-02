package com.truckmgmt.customer

import android.app.Application
import com.google.firebase.FirebaseApp
import com.truckmgmt.shared.fcm.FcmTokenHelper
import com.truckmgmt.shared.notification.TruckNotificationHelper

class CustomerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        PlacesHelper.ensureInitialized(this, BuildConfig.MAPS_API_KEY)
        TruckNotificationHelper.ensureChannels(this)
        FcmTokenHelper.refreshToken(this, FcmTokenHelper.Role.CUSTOMER)
    }
}
