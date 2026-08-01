package com.truckmgmt.driver.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.truckmgmt.shared.TruckMgmtConstants

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(TruckMgmtConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(TruckMgmtConstants.PREF_PERMISSIONS_GRANTED, false)) return
        context.startForegroundService(Intent(context, MonitoringForegroundService::class.java))
    }
}
