package com.truckmgmt.driver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.truckmgmt.shared.TruckMgmtConstants

class DeviceLockActivity : AppCompatActivity() {
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TruckMgmtConstants.ACTION_DEVICE_UNLOCK) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "Device locked by dispatcher\nUnlock only via dispatcher command"
            textSize = 20f
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
        val filter = IntentFilter(TruckMgmtConstants.ACTION_DEVICE_UNLOCK)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(unlockReceiver, filter)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back while locked
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }
}
