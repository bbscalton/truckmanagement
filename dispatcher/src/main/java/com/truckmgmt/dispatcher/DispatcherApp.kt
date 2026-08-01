package com.truckmgmt.dispatcher

import android.app.Application
import com.google.firebase.FirebaseApp

class DispatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
