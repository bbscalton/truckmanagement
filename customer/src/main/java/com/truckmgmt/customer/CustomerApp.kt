package com.truckmgmt.customer

import android.app.Application
import com.google.firebase.FirebaseApp

class CustomerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
