package com.truckmgmt.dispatcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class DashboardActivity : AppCompatActivity(), OnMapReadyCallback {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var fleetId: String? = null
    private var map: GoogleMap? = null
    private var driversListener: ListenerRegistration? = null
    private var stopsListener: ListenerRegistration? = null
    private lateinit var contentTitle: TextView
    private lateinit var contentBody: TextView
    private lateinit var statusLine: TextView
    private lateinit var contentCard: View
    private lateinit var mapContainer: View
    private var currentSection = "live_map"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val nav = findViewById<NavigationView>(R.id.navView)
        contentTitle = findViewById(R.id.contentTitle)
        contentBody = findViewById(R.id.contentBody)
        statusLine = findViewById(R.id.statusLine)
        contentCard = findViewById(R.id.contentCard)
        mapContainer = findViewById(R.id.mapContainer)

        val toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.app_name, R.string.app_name)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        nav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_live_map -> showSection("live_map")
                R.id.nav_trucks -> showSection("trucks")
                R.id.nav_drivers -> showSection("drivers")
                R.id.nav_pair -> showPairDialog()
                R.id.nav_requests -> showSection("requests")
                R.id.nav_deliveries -> showSection("deliveries")
                R.id.nav_playback -> showSection("playback")
                R.id.nav_stops -> showSection("stops")
                R.id.nav_payments -> showSection("payments")
                R.id.nav_totals -> showSection("totals")
                R.id.nav_chat -> startActivity(Intent(this, FleetChatActivity::class.java))
                R.id.nav_activity -> showSection("activity")
                R.id.nav_settings -> showSection("settings")
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, AuthActivity::class.java))
                    finish()
                }
            }
            true
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        lifecycleScope.launch { loadFleet() }
        showSection("live_map")
    }

    private suspend fun loadFleet() {
        val uid = auth.currentUser?.uid ?: return
        val profile = db.collection(TruckMgmtConstants.COL_DISPATCHER_PROFILES).document(uid).get().await()
        fleetId = profile.getString("primaryFleetId")
        listenDrivers()
        listenStops()
    }

    private fun showSection(section: String) {
        currentSection = section
        contentBody.setOnLongClickListener(null)
        val showMap = section == "live_map" || section == "playback" || section == "stops"
        mapContainer.visibility = if (showMap) View.VISIBLE else View.GONE
        contentCard.visibility = if (section == "live_map") View.GONE else View.VISIBLE
        contentBody.visibility = if (showMap && section == "live_map") View.GONE else View.VISIBLE
        statusLine.text = when (section) {
            "live_map" -> "Satellite live tracking"
            "trucks" -> "Fleet trucks"
            "drivers" -> "Paired drivers"
            "requests" -> "Open delivery requests"
            "deliveries" -> "Active deliveries"
            "playback" -> "Trip route replay"
            "stops" -> "Recent driver stops"
            "payments" -> "Accepted payments"
            "totals" -> "Fleet lifetime totals"
            "activity" -> "Monitored activity"
            "settings" -> "Account & fleet ID"
            else -> "TruckMgmt operations"
        }

        when (section) {
            "live_map" -> {
                contentTitle.text = "Live fleet map"
                contentBody.text = "Satellite view of all trucks."
            }
            "trucks" -> {
                contentTitle.text = "Trucks"
                contentBody.text = "Loading trucks…"
                lifecycleScope.launch { loadTrucks() }
            }
            "drivers" -> {
                contentTitle.text = "Drivers"
                contentBody.text = "Loading drivers…"
                lifecycleScope.launch { loadDriversList() }
            }
            "requests" -> {
                contentTitle.text = "Delivery requests"
                contentBody.text = "Loading…"
                lifecycleScope.launch { loadRequests() }
            }
            "deliveries" -> {
                contentTitle.text = "Active deliveries"
                contentBody.text = "Loading…"
                lifecycleScope.launch { loadDeliveries() }
            }
            "playback" -> {
                contentTitle.text = "Trip playback"
                contentBody.text = "Select a trip to replay path on the map."
                lifecycleScope.launch { loadPlayback() }
            }
            "stops" -> {
                contentTitle.text = "Stops"
                contentBody.text = "Recent driver stops plotted on the map."
            }
            "payments" -> {
                contentTitle.text = "Payments"
                contentBody.text = "Only payments accepted by drivers appear here."
                lifecycleScope.launch { loadPayments() }
            }
            "totals" -> {
                contentTitle.text = "Trip totals"
                lifecycleScope.launch { loadTotals() }
            }
            "activity" -> {
                contentTitle.text = "Monitored activity"
                lifecycleScope.launch { loadActivity() }
            }
            "settings" -> {
                contentTitle.text = "Settings"
                val fid = fleetId ?: "—"
                contentBody.text =
                    "Fleet ID: $fid\nShare this code with customers (Customer app → Register or Profile).\nLong-press here to copy.\n\nSigned in as ${auth.currentUser?.email}"
                contentBody.setOnLongClickListener {
                    val id = fleetId
                    if (!id.isNullOrBlank()) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Fleet ID", id))
                        Toast.makeText(this, "Fleet ID copied", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
        }
    }

    private fun showPairDialog() {
        val input = EditText(this).apply { hint = "Driver display name" }
        AlertDialog.Builder(this)
            .setTitle("Pair driver device")
            .setMessage("Creates a 6-digit code (30 min). Enter it on the driver phone.")
            .setView(input)
            .setPositiveButton("Create code") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val fid = fleetId ?: return@launch
                        val code = (100000..999999).random().toString()
                        val name = input.text.toString().ifBlank { "Driver" }
                        db.collection(TruckMgmtConstants.COL_PAIRING_CODES).document(code).set(
                            mapOf(
                                "fleetId" to fid,
                                "driverName" to name,
                                "createdBy" to auth.currentUser?.uid,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "expiresAt" to System.currentTimeMillis() + TruckMgmtConstants.PAIRING_CODE_TTL_MS,
                                "used" to false,
                            )
                        ).await()
                        // Also ensure a truck placeholder can be added
                        AlertDialog.Builder(this@DashboardActivity)
                            .setTitle("Pairing code")
                            .setMessage(code)
                            .setPositiveButton("OK", null)
                            .show()
                    } catch (e: Exception) {
                        Toast.makeText(this@DashboardActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNeutralButton("Add truck") { _, _ -> showAddTruckDialog() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTruckDialog() {
        val plate = EditText(this).apply { hint = "Plate / label" }
        AlertDialog.Builder(this)
            .setTitle("Add truck")
            .setView(plate)
            .setPositiveButton("Add") { _, _ ->
                lifecycleScope.launch {
                    val fid = fleetId ?: return@launch
                    val id = UUID.randomUUID().toString().take(8)
                    db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                        .collection(TruckMgmtConstants.COL_TRUCKS).document(id)
                        .set(
                            mapOf(
                                "label" to plate.text.toString().ifBlank { "Truck $id" },
                                "plate" to plate.text.toString(),
                                "status" to "idle",
                                "createdAt" to FieldValue.serverTimestamp(),
                            )
                        ).await()
                    Toast.makeText(this@DashboardActivity, "Truck added", Toast.LENGTH_SHORT).show()
                    showSection("trucks")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun loadTrucks() {
        val fid = fleetId ?: return
        val snap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_TRUCKS).get().await()
        contentBody.text = if (snap.isEmpty) "No trucks yet. Use Pair device → Add truck."
        else snap.documents.joinToString("\n") {
            "${it.getString("label")} (${it.getString("plate")}) — ${it.getString("status")} driver=${it.getString("assignedDriverId") ?: "—"}"
        }
    }

    private suspend fun loadDriversList() {
        val fid = fleetId ?: return
        val snap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DRIVERS).get().await()
        contentBody.text = if (snap.isEmpty) "No drivers paired yet."
        else snap.documents.joinToString("\n") {
            "${it.getString("displayName")} online=${it.getBoolean("online")} last=${it.getLong("lastHeartbeatAt")}"
        }
    }

    private suspend fun loadRequests() {
        val fid = fleetId ?: return
        val snap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERY_REQUESTS)
            .whereIn("status", listOf(
                TruckMgmtConstants.STATUS_REQUESTED,
                TruckMgmtConstants.STATUS_DISPATCHER_REVIEW,
                TruckMgmtConstants.STATUS_AUTO_NEAREST,
            ))
            .get().await()
        if (snap.isEmpty) {
            contentBody.text = "No open requests."
            return
        }
        val lines = snap.documents.map { doc ->
            val id = doc.id
            val pickup = doc.getString("pickupAddress") ?: "Pickup"
            val status = doc.getString("status")
            "$id — $pickup ($status)"
        }
        contentBody.text = lines.joinToString("\n") + "\n\nTap Assign on a request (long-press list coming; use dialog)."
        // Quick assign first request to first online driver
        AlertDialog.Builder(this)
            .setTitle("Assign first request?")
            .setMessage(lines.first())
            .setPositiveButton("Assign nearest / first driver") { _, _ ->
                lifecycleScope.launch { assignRequest(snap.documents.first().id) }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private suspend fun assignRequest(requestId: String) {
        val fid = fleetId ?: return
        val reqRef = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERY_REQUESTS).document(requestId)
        val req = reqRef.get().await()
        val drivers = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DRIVERS)
            .whereEqualTo("online", true)
            .get().await()
        val driver = drivers.documents.firstOrNull()
        if (driver == null) {
            Toast.makeText(this, "No online drivers", Toast.LENGTH_LONG).show()
            return
        }
        val deliveryRef = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES).document()
        val data = req.data?.toMutableMap() ?: mutableMapOf()
        data["status"] = TruckMgmtConstants.STATUS_ASSIGNED
        data["assignedDriverId"] = driver.id
        data["assignedTruckId"] = driver.getString("truckId")
        data["requestId"] = requestId
        data["updatedAt"] = FieldValue.serverTimestamp()
        deliveryRef.set(data).await()
        reqRef.update(
            mapOf(
                "status" to TruckMgmtConstants.STATUS_ASSIGNED,
                "deliveryId" to deliveryRef.id,
                "assignedDriverId" to driver.id,
            )
        ).await()
        Toast.makeText(this, "Assigned to ${driver.getString("displayName")}", Toast.LENGTH_SHORT).show()
        showSection("deliveries")
    }

    private suspend fun loadDeliveries() {
        val fid = fleetId ?: return
        val snap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES).get().await()
        contentBody.text = if (snap.isEmpty) "No deliveries."
        else snap.documents.joinToString("\n") {
            "${it.id.take(8)} — ${it.getString("status")} driver=${it.getString("assignedDriverId")}"
        }
    }

    private suspend fun loadPlayback() {
        val fid = fleetId ?: return
        val trips = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_TRIPS)
            .limit(10)
            .get().await()
        if (trips.isEmpty) {
            contentBody.text = "No completed trips yet."
            return
        }
        contentBody.text = trips.documents.joinToString("\n") {
            "${it.id.take(8)} cost=${it.getDouble("cost")} pts=${it.getLong("pointCount")}"
        }
        val first = trips.documents.first()
        val deliveryId = first.getString("deliveryId") ?: return
        val trail = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_LOCATION_TRAIL)
            .whereEqualTo("deliveryId", deliveryId)
            .orderBy("ts")
            .get().await()
        val points = trail.documents.mapNotNull { d ->
            val lat = d.getDouble("lat") ?: return@mapNotNull null
            val lng = d.getDouble("lng") ?: return@mapNotNull null
            LatLng(lat, lng)
        }
        map?.clear()
        if (points.isNotEmpty()) {
            map?.addPolyline(PolylineOptions().addAll(points).width(8f).color(0xFFE67E22.toInt()))
            map?.addMarker(MarkerOptions().position(points.first()).title("Start"))
            map?.addMarker(MarkerOptions().position(points.last()).title("End"))
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(points.last(), 14f))
        }
    }

    private suspend fun loadPayments() {
        val fid = fleetId ?: return
        val snap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_PAYMENTS)
            .whereEqualTo("visibleToDispatcher", true)
            .get().await()
        contentBody.text = if (snap.isEmpty) "No accepted payments yet."
        else snap.documents.joinToString("\n") {
            "$${it.getDouble("amount")} — delivery ${it.getString("deliveryId")?.take(8)}"
        }
    }

    private suspend fun loadTotals() {
        val fid = fleetId ?: return
        val fleet = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid).get().await()
        contentBody.text = "Trips: ${fleet.getLong("tripCount") ?: 0}\nRevenue: ${fleet.getDouble("totalRevenue") ?: 0.0}"
    }

    private suspend fun loadActivity() {
        val fid = fleetId ?: return
        val snap = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_ACTIVITY_LOGS)
            .limit(40)
            .get().await()
        contentBody.text = if (snap.isEmpty) "No activity yet."
        else snap.documents.joinToString("\n") {
            "${it.getString("type")}: ${it.getString("summary")}"
        }
    }

    private fun listenDrivers() {
        val fid = fleetId ?: return
        driversListener?.remove()
        driversListener = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DRIVERS)
            .addSnapshotListener { snap, _ ->
                if (currentSection != "live_map" && currentSection != "stops") return@addSnapshotListener
                val gmap = map ?: return@addSnapshotListener
                // Keep stop markers if on stops; otherwise refresh truck markers
                if (currentSection == "live_map") {
                    val online = snap?.documents?.count { it.getBoolean("online") == true } ?: 0
                    val total = snap?.size ?: 0
                    statusLine.text = "$online of $total drivers online · Satellite live tracking"
                    gmap.clear()
                    snap?.documents?.forEach { d ->
                        val lat = d.getDouble("lastLat") ?: return@forEach
                        val lng = d.getDouble("lastLng") ?: return@forEach
                        val online = d.getBoolean("online") == true
                        gmap.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title(d.getString("displayName") ?: "Driver")
                                .snippet(if (online) "Online" else "Offline")
                                .icon(
                                    BitmapDescriptorFactory.defaultMarker(
                                        if (online) BitmapDescriptorFactory.HUE_AZURE
                                        else BitmapDescriptorFactory.HUE_ORANGE
                                    )
                                )
                        )
                    }
                    snap?.documents?.firstOrNull()?.let { d ->
                        val lat = d.getDouble("lastLat")
                        val lng = d.getDouble("lastLng")
                        if (lat != null && lng != null) {
                            gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 12f))
                        }
                    }
                }
            }
    }

    private fun listenStops() {
        val fid = fleetId ?: return
        stopsListener?.remove()
        stopsListener = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_STOPS)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (currentSection != "stops") return@addSnapshotListener
                val gmap = map ?: return@addSnapshotListener
                gmap.clear()
                snap?.documents?.forEach { d ->
                    val lat = d.getDouble("lat") ?: return@forEach
                    val lng = d.getDouble("lng") ?: return@forEach
                    gmap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title("Stop")
                            .snippet(d.getString("driverId"))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                }
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        googleMap.uiSettings.isZoomControlsEnabled = true
        listenDrivers()
    }

    override fun onDestroy() {
        driversListener?.remove()
        stopsListener?.remove()
        super.onDestroy()
    }
}
