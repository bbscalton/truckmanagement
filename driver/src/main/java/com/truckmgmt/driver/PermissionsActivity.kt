package com.truckmgmt.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * One-time company-device permission hub. After grant, core location/FGS
 * is not re-prompted on every launch (soft lockdown Phase 1).
 */
class PermissionsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            markGrantedAndContinue()
        } else {
            Toast.makeText(this, "Location is required for delivery tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        findViewById<TextView>(R.id.policyText).text =
            "This company device is for deliveries only. Location, notifications, and " +
                "communication summaries are monitored by your dispatcher. Grant permissions once."

        findViewById<Button>(R.id.btnGrant).setOnClickListener { requestAll() }

        if (prefs.getBoolean(TruckMgmtConstants.PREF_PERMISSIONS_GRANTED, false)) {
            goHome()
        }
    }

    private fun requestAll() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun markGrantedAndContinue() {
        prefs.edit().putBoolean(TruckMgmtConstants.PREF_PERMISSIONS_GRANTED, true).apply()
        lifecycleScope.launch {
            val fleetId = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null) ?: return@launch
            val deviceId = prefs.getString(TruckMgmtConstants.PREF_DEVICE_ID, null) ?: return@launch
            FirebaseFirestore.getInstance()
                .collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                .collection(TruckMgmtConstants.COL_DEVICES).document(deviceId)
                .update("permissionsGranted", true).await()
            goHome()
        }
    }

    private fun goHome() {
        startActivity(Intent(this, DriverHomeActivity::class.java))
        finish()
    }
}
