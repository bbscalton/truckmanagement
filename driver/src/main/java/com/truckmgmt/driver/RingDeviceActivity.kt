package com.truckmgmt.driver

import android.media.RingtoneManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RingDeviceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ringtone = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
        ringtone?.play()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Dispatcher is ringing this device"
            textSize = 20f
        })
        root.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                ringtone?.stop()
                finish()
            }
        })
        setContentView(root)
    }
}
