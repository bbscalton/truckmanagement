package com.truckmgmt.driver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PairingActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        findViewById<Button>(R.id.btnPair).setOnClickListener {
            val code = findViewById<EditText>(R.id.inputCode).text.toString().trim()
            lifecycleScope.launch { pair(code) }
        }
    }

    private suspend fun pair(code: String) {
        try {
            if (code.length < 4) {
                Toast.makeText(this, "Enter pairing code", Toast.LENGTH_SHORT).show()
                return
            }
            val codeRef = db.collection(TruckMgmtConstants.COL_PAIRING_CODES).document(code)
            val codeDoc = codeRef.get().await()
            if (!codeDoc.exists()) {
                Toast.makeText(this, "Invalid code", Toast.LENGTH_LONG).show()
                return
            }
            if (codeDoc.getBoolean("used") == true) {
                Toast.makeText(this, "Code already used", Toast.LENGTH_LONG).show()
                return
            }
            val expiresAt = codeDoc.getLong("expiresAt") ?: 0L
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                Toast.makeText(this, "Code expired", Toast.LENGTH_LONG).show()
                return
            }
            val fleetId = codeDoc.getString("fleetId") ?: return
            val driverName = codeDoc.getString("driverName") ?: "Driver"

            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            val uid = auth.currentUser?.uid ?: return
            val deviceId = UUID.randomUUID().toString()

            db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                .collection(TruckMgmtConstants.COL_DRIVERS).document(uid)
                .set(
                    mapOf(
                        "displayName" to driverName,
                        "deviceId" to deviceId,
                        "online" to false,
                        "authUid" to uid,
                        "pairedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

            db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                .collection(TruckMgmtConstants.COL_DEVICES).document(deviceId)
                .set(
                    mapOf(
                        "authUid" to uid,
                        "driverId" to uid,
                        "label" to driverName,
                        "pairedAt" to FieldValue.serverTimestamp(),
                        "permissionsGranted" to false,
                    )
                ).await()

            codeRef.update(mapOf("used" to true, "usedBy" to uid, "deviceId" to deviceId)).await()

            getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(TruckMgmtConstants.PREF_FLEET_ID, fleetId)
                .putString(TruckMgmtConstants.PREF_DEVICE_ID, deviceId)
                .putString(TruckMgmtConstants.PREF_DRIVER_ID, uid)
                .apply()

            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }
}
