package com.truckmgmt.driver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.truckmgmt.shared.TruckMgmtConstants

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE)
        val fleetId = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null)
        val perms = prefs.getBoolean(TruckMgmtConstants.PREF_PERMISSIONS_GRANTED, false)
        when {
            fleetId.isNullOrBlank() -> startActivity(Intent(this, PairingActivity::class.java))
            !perms -> startActivity(Intent(this, PermissionsActivity::class.java))
            else -> startActivity(Intent(this, DriverHomeActivity::class.java))
        }
        finish()
    }
}
