package com.truckmgmt.driver.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.truckmgmt.driver.DriverHomeActivity
import com.truckmgmt.driver.FleetChatActivity
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.fcm.FcmTokenHelper
import com.truckmgmt.shared.notification.TruckNotificationHelper

class DriverFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data[TruckMgmtConstants.FCM_TYPE] ?: return
        val title = message.notification?.title ?: message.data["title"] ?: "TruckMgmt"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val launch = when (type) {
            TruckMgmtConstants.FCM_TYPE_CHAT -> Intent(this, FleetChatActivity::class.java)
            else -> Intent(this, DriverHomeActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data.forEach { (k, v) -> putExtra(k, v) }
        }
        val channel = when (type) {
            TruckMgmtConstants.FCM_TYPE_NEW_REQUEST, TruckMgmtConstants.FCM_TYPE_DELIVERY -> TruckMgmtConstants.CHANNEL_JOBS
            TruckMgmtConstants.FCM_TYPE_CHAT -> TruckMgmtConstants.CHANNEL_CHAT
            else -> TruckMgmtConstants.CHANNEL_ALERTS
        }
        TruckNotificationHelper.show(
            context = this,
            channelId = channel,
            notificationId = TruckMgmtConstants.NOTIF_ID_CHAT_BASE + type.hashCode(),
            title = title,
            body = body,
            data = message.data,
            launchIntent = launch,
        )
    }

    override fun onNewToken(token: String) {
        FcmTokenHelper.refreshToken(this, FcmTokenHelper.Role.DRIVER)
    }
}
