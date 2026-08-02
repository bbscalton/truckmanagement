package com.truckmgmt.customer.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.truckmgmt.customer.CustomerHomeActivity
import com.truckmgmt.customer.TripChatActivity
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.fcm.FcmTokenHelper
import com.truckmgmt.shared.notification.TruckNotificationHelper

class CustomerFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data[TruckMgmtConstants.FCM_TYPE] ?: return
        val title = message.notification?.title ?: message.data["title"] ?: "TruckMgmt"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val deliveryId = message.data[TruckMgmtConstants.FCM_DELIVERY_ID]
        val launch = when (type) {
            TruckMgmtConstants.FCM_TYPE_CHAT -> Intent(this, TripChatActivity::class.java).apply {
                if (deliveryId != null) putExtra("deliveryId", deliveryId)
            }
            else -> Intent(this, CustomerHomeActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data.forEach { (k, v) -> putExtra(k, v) }
        }
        TruckNotificationHelper.show(
            context = this,
            channelId = if (type == TruckMgmtConstants.FCM_TYPE_CHAT) TruckMgmtConstants.CHANNEL_CHAT else TruckMgmtConstants.CHANNEL_JOBS,
            notificationId = TruckMgmtConstants.NOTIF_ID_CHAT_BASE + (deliveryId?.hashCode()?.and(0xFF) ?: type.hashCode()),
            title = title,
            body = body,
            data = message.data,
            launchIntent = launch,
        )
    }

    override fun onNewToken(token: String) {
        FcmTokenHelper.refreshToken(this, FcmTokenHelper.Role.CUSTOMER)
    }
}
