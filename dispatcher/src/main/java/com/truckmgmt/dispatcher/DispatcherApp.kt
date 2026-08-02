package com.truckmgmt.dispatcher

import android.app.Application
import com.google.firebase.FirebaseApp
import com.truckmgmt.shared.fcm.FcmTokenHelper
import com.truckmgmt.shared.notification.TruckNotificationHelper

class DispatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        TruckNotificationHelper.ensureChannels(this)
        FcmTokenHelper.refreshToken(this, FcmTokenHelper.Role.DISPATCHER)
    }
}
