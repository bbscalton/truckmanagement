package com.truckmgmt.driver

import android.app.Application
import com.google.firebase.FirebaseApp

class DriverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
