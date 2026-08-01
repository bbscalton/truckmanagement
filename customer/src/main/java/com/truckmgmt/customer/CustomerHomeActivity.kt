package com.truckmgmt.customer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.truckmgmt.shared.DeliveryStatusLabels
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CustomerHomeActivity : AppCompatActivity(), OnMapReadyCallback {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var fleetId: String? = null
    private var map: GoogleMap? = null
    private var deliveryListener: ListenerRegistration? = null
    private var truckListener: ListenerRegistration? = null
    private lateinit var infoText: TextView
    private var activeDeliveryId: String? = null
    private var activeDriverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_home)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val nav = findViewById<NavigationView>(R.id.navView)
        infoText = findViewById(R.id.infoText)

        ActionBarDrawerToggle(this, drawer, toolbar, R.string.app_name, R.string.app_name).also {
            drawer.addDrawerListener(it)
            it.syncState()
        }

        nav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_map -> Unit
                R.id.nav_schedule -> startActivity(Intent(this, ScheduleDeliveryActivity::class.java))
                R.id.nav_deliveries -> loadMyDeliveries()
                R.id.nav_track -> listenActiveTruck()
                R.id.nav_pay -> enterPayment()
                R.id.nav_chat -> {
                    val id = activeDeliveryId
                    if (id == null) Toast.makeText(this, "No active delivery", Toast.LENGTH_SHORT).show()
                    else startActivity(Intent(this, TripChatActivity::class.java).putExtra("deliveryId", id))
                }
                R.id.nav_profile -> {
                    infoText.text = "Signed in as ${auth.currentUser?.email}\nFleet: $fleetId"
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, AuthActivity::class.java))
                    finish()
                }
            }
            true
        }

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleDeliveryActivity::class.java))
        }
        findViewById<Button>(R.id.btnPay).setOnClickListener { enterPayment() }

        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment).getMapAsync(this)
        lifecycleScope.launch { loadProfile() }
    }

    private suspend fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        val profile = db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).get().await()
        fleetId = profile.getString("primaryFleetId")
        infoText.text = "Fleet: ${fleetId ?: "link a fleet ID in profile"}"
        listenMyDeliveries()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun listenMyDeliveries() {
        val fid = fleetId ?: return
        val uid = auth.currentUser?.uid ?: return
        deliveryListener?.remove()
        deliveryListener = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES)
            .whereEqualTo("customerUid", uid)
            .addSnapshotListener { snap, _ ->
                val active = snap?.documents?.firstOrNull { d ->
                    val s = d.getString("status") ?: ""
                    DeliveryStatusLabels.isActive(s) ||
                        s == TruckMgmtConstants.STATUS_ASSIGNED ||
                        s == TruckMgmtConstants.STATUS_PAYMENT_PENDING
                }
                if (active == null) {
                    infoText.text = "No active delivery"
                    activeDeliveryId = null
                    activeDriverId = null
                    return@addSnapshotListener
                }
                activeDeliveryId = active.id
                activeDriverId = active.getString("assignedDriverId")
                val status = active.getString("status") ?: ""
                infoText.text = "Delivery ${active.id.take(8)}\n$status"
                if (DeliveryStatusLabels.customerCanSeeTruck(status)) {
                    listenActiveTruck()
                }
            }
    }

    private fun loadMyDeliveries() {
        listenMyDeliveries()
        Toast.makeText(this, "Showing latest delivery status", Toast.LENGTH_SHORT).show()
    }

    private fun listenActiveTruck() {
        val fid = fleetId ?: return
        val driverId = activeDriverId ?: run {
            Toast.makeText(this, "Truck visible after driver accepts", Toast.LENGTH_SHORT).show()
            return
        }
        truckListener?.remove()
        truckListener = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DRIVERS).document(driverId)
            .addSnapshotListener { doc, _ ->
                val lat = doc?.getDouble("lastLat") ?: return@addSnapshotListener
                val lng = doc.getDouble("lastLng") ?: return@addSnapshotListener
                val pos = LatLng(lat, lng)
                map?.clear()
                map?.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title("Your truck")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
            }
    }

    private fun enterPayment() {
        val fid = fleetId ?: return
        val deliveryId = activeDeliveryId ?: run {
            Toast.makeText(this, "No delivery to pay for", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Amount paid"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle("Enter amount paid")
            .setMessage("Agree price in chat/call first. Driver must accept for dispatcher to see it.")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null) {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                    .collection(TruckMgmtConstants.COL_PAYMENTS)
                    .add(
                        mapOf(
                            "deliveryId" to deliveryId,
                            "customerUid" to auth.currentUser?.uid,
                            "driverId" to activeDriverId,
                            "amount" to amount,
                            "acceptedByDriver" to false,
                            "visibleToDispatcher" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                    .collection(TruckMgmtConstants.COL_DELIVERIES).document(deliveryId)
                    .update("status", TruckMgmtConstants.STATUS_PAYMENT_PENDING)
                Toast.makeText(this, "Waiting for driver to accept payment", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        deliveryListener?.remove()
        truckListener?.remove()
        super.onDestroy()
    }
}
