package com.truckmgmt.dispatcher.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.truckmgmt.dispatcher.DashboardActivity
import com.truckmgmt.dispatcher.FleetChatActivity
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.fcm.FcmTokenHelper
import com.truckmgmt.shared.notification.TruckNotificationHelper

class DispatcherFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data[TruckMgmtConstants.FCM_TYPE] ?: return
        val title = message.notification?.title ?: message.data["title"] ?: "TruckMgmt"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val launch = when (type) {
            TruckMgmtConstants.FCM_TYPE_CHAT -> Intent(this, FleetChatActivity::class.java)
            else -> Intent(this, DashboardActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data.forEach { (k, v) -> putExtra(k, v) }
        }
        val channel = when (type) {
            TruckMgmtConstants.FCM_TYPE_NEW_REQUEST -> TruckMgmtConstants.CHANNEL_ALERTS
            TruckMgmtConstants.FCM_TYPE_CHAT -> TruckMgmtConstants.CHANNEL_CHAT
            else -> TruckMgmtConstants.CHANNEL_JOBS
        }
        val notifId = when (type) {
            TruckMgmtConstants.FCM_TYPE_NEW_REQUEST -> TruckMgmtConstants.NOTIF_ID_REQUEST
            else -> TruckMgmtConstants.NOTIF_ID_CHAT_BASE + (message.data[TruckMgmtConstants.FCM_DELIVERY_ID]?.hashCode()?.and(0xFF) ?: 0)
        }
        TruckNotificationHelper.show(
            context = this,
            channelId = channel,
            notificationId = notifId,
            title = title,
            body = body,
            data = message.data,
            launchIntent = launch,
            fullScreen = type == TruckMgmtConstants.FCM_TYPE_NEW_REQUEST,
        )
    }

    override fun onNewToken(token: String) {
        FcmTokenHelper.refreshToken(this, FcmTokenHelper.Role.DISPATCHER)
    }
}
