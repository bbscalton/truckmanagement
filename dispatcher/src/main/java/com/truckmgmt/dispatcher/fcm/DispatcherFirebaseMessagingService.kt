package com.truckmgmt.dispatcher.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DispatcherFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // Notifications are displayed by system when app is backgrounded.
        // Foreground: rely on Firestore listeners in DashboardActivity.
    }

    override fun onNewToken(token: String) {
        // Token persisted when dispatcher profile loads in a later enhancement.
    }
}
