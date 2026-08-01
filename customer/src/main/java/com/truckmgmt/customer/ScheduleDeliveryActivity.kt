package com.truckmgmt.customer

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

class ScheduleDeliveryActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            lifecycleScope.launch { submit() }
        }
    }

    private suspend fun submit() {
        try {
            val uid = auth.currentUser?.uid ?: return
            val profile = db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).get().await()
            val fleetId = profile.getString("primaryFleetId")
            if (fleetId.isNullOrBlank()) {
                Toast.makeText(this, "No fleet linked. Re-register with fleet ID.", Toast.LENGTH_LONG).show()
                return
            }
            val pickup = findViewById<EditText>(R.id.inputPickup).text.toString().trim()
            val dropoff = findViewById<EditText>(R.id.inputDropoff).text.toString().trim()
            val notes = findViewById<EditText>(R.id.inputNotes).text.toString().trim()
            val whenText = findViewById<EditText>(R.id.inputWhen).text.toString().trim()
            val pickupLat = findViewById<EditText>(R.id.inputPickupLat).text.toString().toDoubleOrNull()
            val pickupLng = findViewById<EditText>(R.id.inputPickupLng).text.toString().toDoubleOrNull()
            val customerLat = findViewById<EditText>(R.id.inputCustomerLat).text.toString().toDoubleOrNull()
            val customerLng = findViewById<EditText>(R.id.inputCustomerLng).text.toString().toDoubleOrNull()

            if (pickup.isEmpty() || dropoff.isEmpty()) {
                Toast.makeText(this, "Pickup and dropoff required", Toast.LENGTH_SHORT).show()
                return
            }

            val data = hashMapOf<String, Any>(
                "customerUid" to uid,
                "pickupAddress" to pickup,
                "dropoffAddress" to dropoff,
                "notes" to notes,
                "scheduledFor" to whenText,
                "status" to TruckMgmtConstants.STATUS_DISPATCHER_REVIEW,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "dispatcherTimeoutAt" to System.currentTimeMillis() + TruckMgmtConstants.NEAREST_DRIVER_TIMEOUT_MS,
            )
            pickupLat?.let { data["pickupLat"] = it }
            pickupLng?.let { data["pickupLng"] = it }
            customerLat?.let { data["customerLat"] = it }
            customerLng?.let { data["customerLng"] = it }

            db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                .collection(TruckMgmtConstants.COL_DELIVERY_REQUESTS)
                .add(data).await()

            Toast.makeText(this, "Request sent to dispatcher", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }
}
