package com.truckmgmt.driver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.truckmgmt.driver.monitoring.MonitoringForegroundService
import com.truckmgmt.shared.DeliveryStatusLabels
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DriverHomeActivity : AppCompatActivity(), OnMapReadyCallback {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE) }
    private var map: GoogleMap? = null
    private var jobsListener: ListenerRegistration? = null
    private var customerMarkerListener: ListenerRegistration? = null
    private lateinit var statusText: TextView
    private lateinit var jobText: TextView
    private var activeDeliveryId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_home)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val nav = findViewById<NavigationView>(R.id.navView)
        statusText = findViewById(R.id.statusText)
        jobText = findViewById(R.id.jobText)

        ActionBarDrawerToggle(this, drawer, toolbar, R.string.app_name, R.string.app_name).also {
            drawer.addDrawerListener(it)
            it.syncState()
        }

        nav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_map -> { /* already on map */ }
                R.id.nav_jobs -> loadJobs()
                R.id.nav_arrive -> markArrived()
                R.id.nav_deliver -> markDelivered()
                R.id.nav_chat -> startActivity(Intent(this, FleetChatActivity::class.java))
                R.id.nav_payments -> showAcceptPayment()
                R.id.nav_online -> toggleOnline()
            }
            true
        }

        findViewById<Button>(R.id.btnAcceptJob).setOnClickListener { acceptAssignedJob() }
        findViewById<Button>(R.id.btnArrive).setOnClickListener { markArrived() }
        findViewById<Button>(R.id.btnDeliver).setOnClickListener { markDelivered() }

        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment).getMapAsync(this)

        startService(Intent(this, MonitoringForegroundService::class.java))
        setOnline(true)
        loadJobs()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun fleetId() = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null)
    private fun driverId() = prefs.getString(TruckMgmtConstants.PREF_DRIVER_ID, auth.currentUser?.uid)

    private fun setOnline(online: Boolean) {
        prefs.edit().putBoolean(TruckMgmtConstants.PREF_ONLINE, online).apply()
        statusText.text = if (online) "Status: Online" else "Status: Offline"
        val fid = fleetId() ?: return
        val did = driverId() ?: return
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DRIVERS).document(did)
            .update("online", online)
    }

    private fun toggleOnline() {
        val online = !prefs.getBoolean(TruckMgmtConstants.PREF_ONLINE, true)
        setOnline(online)
    }

    private fun loadJobs() {
        val fid = fleetId() ?: return
        val did = driverId() ?: return
        jobsListener?.remove()
        jobsListener = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES)
            .whereEqualTo("assignedDriverId", did)
            .addSnapshotListener { snap, _ ->
                val active = snap?.documents?.firstOrNull { d ->
                    val s = d.getString("status") ?: ""
                    DeliveryStatusLabels.isActive(s) || s == TruckMgmtConstants.STATUS_ASSIGNED
                }
                if (active == null) {
                    jobText.text = "No active jobs"
                    activeDeliveryId = null
                    return@addSnapshotListener
                }
                activeDeliveryId = active.id
                val status = active.getString("status")
                jobText.text = "Job ${active.id.take(8)}\n$status\n${active.getString("dropoffAddress") ?: ""}"
                if (DeliveryStatusLabels.customerCanSeeTruck(status ?: "") ||
                    status == TruckMgmtConstants.STATUS_ACCEPTED_BY_DRIVER ||
                    status == TruckMgmtConstants.STATUS_EN_ROUTE
                ) {
                    listenCustomerLocation(active)
                }
            }
    }

    private fun listenCustomerLocation(delivery: com.google.firebase.firestore.DocumentSnapshot) {
        customerMarkerListener?.remove()
        val lat = delivery.getDouble("customerLat")
        val lng = delivery.getDouble("customerLng")
        if (lat != null && lng != null) {
            val pos = LatLng(lat, lng)
            map?.clear()
            map?.addMarker(MarkerOptions().position(pos).title("Customer"))
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
        }
    }

    private fun acceptAssignedJob() {
        val fid = fleetId() ?: return
        val id = activeDeliveryId ?: run {
            Toast.makeText(this, "No assigned job", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES).document(id)
            .update(
                mapOf(
                    "status" to TruckMgmtConstants.STATUS_ACCEPTED_BY_DRIVER,
                    "acceptedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        logActivity("job_accepted", "Accepted delivery $id")
        Toast.makeText(this, "Job accepted — customer can see your truck", Toast.LENGTH_SHORT).show()
    }

    private fun markArrived() {
        updateDeliveryStatus(TruckMgmtConstants.STATUS_ARRIVED, "Arrived at location")
    }

    private fun markDelivered() {
        updateDeliveryStatus(TruckMgmtConstants.STATUS_PAYMENT_PENDING, "Delivered — awaiting payment")
    }

    private fun updateDeliveryStatus(status: String, toast: String) {
        val fid = fleetId() ?: return
        val id = activeDeliveryId ?: return
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (status == TruckMgmtConstants.STATUS_ARRIVED) {
            updates["arrivedAt"] = FieldValue.serverTimestamp()
        }
        if (status == TruckMgmtConstants.STATUS_PAYMENT_PENDING) {
            updates["deliveredAt"] = FieldValue.serverTimestamp()
        }
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES).document(id)
            .update(updates)
        logActivity("status_$status", toast)
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
    }

    private fun showAcceptPayment() {
        val fid = fleetId() ?: return
        val deliveryId = activeDeliveryId ?: run {
            Toast.makeText(this, "No active delivery", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val pending = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_PAYMENTS)
                .whereEqualTo("deliveryId", deliveryId)
                .whereEqualTo("acceptedByDriver", false)
                .get().await()
            val pay = pending.documents.firstOrNull()
            if (pay == null) {
                Toast.makeText(this@DriverHomeActivity, "No payment from customer yet", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val amount = pay.getDouble("amount") ?: 0.0
            AlertDialog.Builder(this@DriverHomeActivity)
                .setTitle("Accept payment?")
                .setMessage("Customer entered $$amount. Accept to notify dispatcher.")
                .setPositiveButton("Accept") { _, _ ->
                    lifecycleScope.launch {
                        pay.reference.update(
                            mapOf(
                                "acceptedByDriver" to true,
                                "visibleToDispatcher" to true,
                                "acceptedAt" to FieldValue.serverTimestamp(),
                            )
                        ).await()
                        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                            .collection(TruckMgmtConstants.COL_DELIVERIES).document(deliveryId)
                            .update(
                                "status", TruckMgmtConstants.STATUS_PAYMENT_ACCEPTED,
                                "updatedAt", FieldValue.serverTimestamp(),
                            ).await()
                        // Trip rollup — Functions also handle this; client-side fallback:
                        val fleetRef = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                        fleetRef.update(
                            mapOf(
                                "tripCount" to FieldValue.increment(1),
                                "totalRevenue" to FieldValue.increment(amount),
                            )
                        ).await()
                        val trail = fleetRef.collection(TruckMgmtConstants.COL_LOCATION_TRAIL)
                            .whereEqualTo("deliveryId", deliveryId).get().await()
                        fleetRef.collection(TruckMgmtConstants.COL_TRIPS).document().set(
                            mapOf(
                                "deliveryId" to deliveryId,
                                "driverId" to driverId(),
                                "cost" to amount,
                                "pointCount" to trail.size().toLong(),
                                "createdAt" to FieldValue.serverTimestamp(),
                            )
                        ).await()
                        Toast.makeText(this@DriverHomeActivity, "Payment accepted", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun logActivity(type: String, summary: String) {
        val fid = fleetId() ?: return
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_ACTIVITY_LOGS)
            .add(
                mapOf(
                    "type" to type,
                    "summary" to summary,
                    "driverId" to driverId(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "ts" to System.currentTimeMillis(),
                )
            )
    }

    override fun onDestroy() {
        jobsListener?.remove()
        customerMarkerListener?.remove()
        super.onDestroy()
    }
}
