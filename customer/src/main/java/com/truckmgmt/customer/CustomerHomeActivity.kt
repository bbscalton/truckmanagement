package com.truckmgmt.customer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.truckmgmt.customer.databinding.ActivityCustomerHomeBinding
import com.truckmgmt.shared.DeliveryStatusLabels
import com.truckmgmt.shared.FleetIdGenerator
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CustomerHomeActivity : AppCompatActivity(), OnMapReadyCallback {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityCustomerHomeBinding
    private var fleetId: String? = null
    private var map: GoogleMap? = null
    private var deliveryListener: ListenerRegistration? = null
    private var truckListener: ListenerRegistration? = null
    private var activeDeliveryId: String? = null
    private var activeDriverId: String? = null
    private var activeStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.btnSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleDeliveryActivity::class.java))
        }
        binding.btnTrackTruck.setOnClickListener { listenActiveTruck() }
        binding.btnLinkFleet.setOnClickListener { showLinkFleetDialog() }
        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showHomePanel()
                R.id.nav_track -> listenActiveTruck()
                R.id.nav_pay -> enterPayment()
                R.id.nav_chat -> openChat()
                R.id.nav_profile -> showProfilePanel()
            }
            true
        }

        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment).getMapAsync(this)
        lifecycleScope.launch { loadProfile() }
    }

    private fun showHomePanel() {
        binding.statusCard.visibility = View.VISIBLE
        binding.profilePanel.visibility = View.GONE
        binding.bottomNav.menu.findItem(R.id.nav_home).isChecked = true
    }

    private fun showProfilePanel() {
        binding.statusCard.visibility = View.GONE
        binding.profilePanel.visibility = View.VISIBLE
        updateProfileTexts()
    }

    private suspend fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        val profile = db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).get().await()
        fleetId = profile.getString("primaryFleetId")?.takeIf { it.isNotBlank() }
        updateHomeTexts()
        if (fleetId != null) {
            listenMyDeliveries()
        } else {
            showLinkFleetDialog()
        }
    }

    private fun updateHomeTexts() {
        val email = auth.currentUser?.email.orEmpty()
        val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        binding.greetingText.text = getString(R.string.home_greeting) + ", $name"
        binding.fleetText.text = fleetId?.let { getString(R.string.home_fleet_linked, it) }
            ?: getString(R.string.home_no_fleet)
    }

    private fun updateProfileTexts() {
        binding.profileEmailText.text = getString(R.string.profile_signed_in, auth.currentUser?.email.orEmpty())
        binding.profileFleetText.text = fleetId?.let { getString(R.string.home_fleet_linked, it) }
            ?: getString(R.string.home_no_fleet)
    }

    private fun showLinkFleetDialog() {
        val input = TextInputEditText(this).apply {
            hint = getString(R.string.profile_fleet_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(fleetId.orEmpty())
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_link_fleet)
            .setMessage(R.string.profile_link_fleet_message)
            .setView(input)
            .setPositiveButton(R.string.profile_link) { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val email = auth.currentUser?.email ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        fleetId = FleetLinkHelper.linkCustomerToFleet(
                            db, uid, email, FleetIdGenerator.normalize(input.text.toString()),
                        )
                        updateHomeTexts()
                        updateProfileTexts()
                        listenMyDeliveries()
                        Toast.makeText(
                            this@CustomerHomeActivity,
                            getString(R.string.profile_linked_toast, fleetId),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (e: FleetNotFoundException) {
                        Toast.makeText(this@CustomerHomeActivity, e.message, Toast.LENGTH_LONG).show()
                    } catch (e: FirebaseFirestoreException) {
                        val msg = when (e.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "Permission denied linking to fleet. Contact your dispatcher."
                            else -> e.message ?: "Could not link fleet"
                        }
                        Toast.makeText(this@CustomerHomeActivity, msg, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@CustomerHomeActivity, e.message ?: "Could not link fleet", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        val default = LatLng(39.8283, -98.5795)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(default, 4f))
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
                    activeDeliveryId = null
                    activeDriverId = null
                    activeStatus = null
                    binding.statusChip.text = getString(R.string.home_no_active_delivery)
                    binding.statusChip.setChipBackgroundColorResource(R.color.app_bg)
                    binding.statusChip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    binding.deliveryDetailText.visibility = View.GONE
                    return@addSnapshotListener
                }
                activeDeliveryId = active.id
                activeDriverId = active.getString("assignedDriverId")
                activeStatus = active.getString("status") ?: ""
                binding.statusChip.text = getString(R.string.home_active_delivery)
                binding.statusChip.setChipBackgroundColorResource(R.color.status_active_bg)
                binding.statusChip.setTextColor(ContextCompat.getColor(this, R.color.status_active_text))
                binding.deliveryDetailText.visibility = View.VISIBLE
                binding.deliveryDetailText.text = buildString {
                    append(getString(R.string.home_delivery_id, active.id.take(8)))
                    append("\n")
                    append(getString(R.string.home_status, activeStatus))
                    active.getString("pickupAddress")?.let { append("\n↑ $it") }
                    active.getString("dropoffAddress")?.let { append("\n↓ $it") }
                }
                if (DeliveryStatusLabels.customerCanSeeTruck(activeStatus!!)) {
                    listenActiveTruck(showToast = false)
                }
            }
    }

    private fun listenActiveTruck(showToast: Boolean = true) {
        binding.bottomNav.menu.findItem(R.id.nav_track).isChecked = true
        showHomePanel()
        val fid = fleetId ?: return
        val driverId = activeDriverId ?: run {
            if (showToast) Toast.makeText(this, R.string.home_track_unavailable, Toast.LENGTH_SHORT).show()
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
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
                )
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
            }
    }

    private fun openChat() {
        binding.bottomNav.menu.findItem(R.id.nav_chat).isChecked = true
        val id = activeDeliveryId
        if (id == null) {
            Toast.makeText(this, R.string.chat_no_active, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, TripChatActivity::class.java).putExtra("deliveryId", id))
    }

    private fun enterPayment() {
        binding.bottomNav.menu.findItem(R.id.nav_pay).isChecked = true
        showHomePanel()
        val fid = fleetId ?: return
        val deliveryId = activeDeliveryId ?: run {
            Toast.makeText(this, R.string.pay_no_delivery, Toast.LENGTH_SHORT).show()
            return
        }
        val input = TextInputEditText(this).apply {
            hint = getString(R.string.pay_amount_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pay_title)
            .setMessage(R.string.pay_message)
            .setView(input)
            .setPositiveButton(R.string.pay_submit) { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null) {
                    Toast.makeText(this, R.string.pay_invalid_amount, Toast.LENGTH_SHORT).show()
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
                        ),
                    )
                db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                    .collection(TruckMgmtConstants.COL_DELIVERIES).document(deliveryId)
                    .update("status", TruckMgmtConstants.STATUS_PAYMENT_PENDING)
                Toast.makeText(this, R.string.pay_waiting, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        deliveryListener?.remove()
        truckListener?.remove()
        super.onDestroy()
    }
}
