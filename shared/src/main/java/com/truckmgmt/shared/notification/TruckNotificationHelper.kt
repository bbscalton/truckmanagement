package com.truckmgmt.shared.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.truckmgmt.shared.TruckMgmtConstants

object TruckNotificationHelper {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        listOf(
            Triple(TruckMgmtConstants.CHANNEL_ALERTS, "Delivery alerts", NotificationManager.IMPORTANCE_HIGH),
            Triple(TruckMgmtConstants.CHANNEL_CHAT, "Chat messages", NotificationManager.IMPORTANCE_HIGH),
            Triple(TruckMgmtConstants.CHANNEL_JOBS, "Job updates", NotificationManager.IMPORTANCE_HIGH),
        ).forEach { (id, name, importance) ->
            val channel = NotificationChannel(id, name, importance).apply {
                enableVibration(true)
                setSound(sound, attrs)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun show(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
        launchIntent: Intent? = null,
        fullScreen: Boolean = false,
    ) {
        ensureChannels(context)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pending)
        data.forEach { (k, v) -> builder.addExtras(android.os.Bundle().apply { putString(k, v) }) }
        if (fullScreen && launchIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(pending, true)
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
