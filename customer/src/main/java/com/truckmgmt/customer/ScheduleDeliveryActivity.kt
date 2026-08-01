package com.truckmgmt.customer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.widget.Autocomplete
import com.truckmgmt.customer.databinding.ActivityScheduleBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ScheduleDeliveryActivity : AppCompatActivity(), OnMapReadyCallback {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityScheduleBinding

    private var map: GoogleMap? = null
    private var marker: com.google.android.gms.maps.model.Marker? = null
    private var currentStep = 0

    private var pickupAddress = ""
    private var dropoffAddress = ""
    private var pickupLat: Double? = null
    private var pickupLng: Double? = null
    private var customerLat: Double? = null
    private var customerLng: Double? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val placesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        try {
            val place = Autocomplete.getPlaceFromIntent(result.data!!)
            val latLng = place.latLng ?: return@registerForActivityResult
            val address = place.address ?: place.name ?: GeocodeHelper.latLngString(latLng)
            applyLocation(latLng, address)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.places_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnUseLocation.setOnClickListener {
            LocationPermissionHelper.requestOrRun(this, locationPermissionLauncher) {
                fetchCurrentLocation()
            }
        }
        binding.btnSearchAddress.setOnClickListener { launchPlacesSearch() }
        binding.btnBack.setOnClickListener { goToStep(currentStep - 1) }
        binding.btnNext.setOnClickListener { onNextClicked() }

        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment).getMapAsync(this)
        updateStepUi()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isScrollGesturesEnabled = true
        googleMap.uiSettings.isZoomGesturesEnabled = true

        if (LocationPermissionHelper.hasLocationPermission(this)) {
            @Suppress("MissingPermission")
            googleMap.isMyLocationEnabled = true
        }

        val default = LatLng(39.8283, -98.5795)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(default, 4f))

        googleMap.setOnMapClickListener { latLng ->
            if (currentStep > 1) return@setOnMapClickListener
            lifecycleScope.launch {
                val address = GeocodeHelper.reverseGeocode(this@ScheduleDeliveryActivity, latLng.latitude, latLng.longitude)
                    ?: GeocodeHelper.latLngString(latLng)
                applyLocation(latLng, address)
            }
        }
    }

    private fun fetchCurrentLocation() {
        Toast.makeText(this, R.string.location_fetching, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val latLng = LocationHelper.getCurrentLatLng(this@ScheduleDeliveryActivity)
                if (latLng == null) {
                    Toast.makeText(this@ScheduleDeliveryActivity, R.string.location_error, Toast.LENGTH_LONG).show()
                    return@launch
                }
                customerLat = latLng.latitude
                customerLng = latLng.longitude
                val address = GeocodeHelper.reverseGeocode(this@ScheduleDeliveryActivity, latLng.latitude, latLng.longitude)
                    ?: GeocodeHelper.latLngString(latLng)
                applyLocation(latLng, address)
            } catch (_: SecurityException) {
                Toast.makeText(this@ScheduleDeliveryActivity, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@ScheduleDeliveryActivity, R.string.location_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchPlacesSearch() {
        if (!PlacesHelper.ensureInitialized(this, BuildConfig.MAPS_API_KEY)) {
            Toast.makeText(this, R.string.places_error, Toast.LENGTH_SHORT).show()
            return
        }
        placesLauncher.launch(PlacesHelper.buildAutocompleteIntent(this))
    }

    private fun applyLocation(latLng: LatLng, address: String) {
        binding.inputAddress.setText(address)
        customerLat = latLng.latitude
        customerLng = latLng.longitude
        if (currentStep == 0) {
            pickupLat = latLng.latitude
            pickupLng = latLng.longitude
        }
        placeMarker(latLng)
    }

    private fun placeMarker(latLng: LatLng) {
        val googleMap = map ?: return
        marker?.remove()
        val hue = if (currentStep == 0) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_GREEN
        marker = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(hue)),
        )
        googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: com.google.android.gms.maps.model.Marker) = Unit
            override fun onMarkerDrag(marker: com.google.android.gms.maps.model.Marker) = Unit
            override fun onMarkerDragEnd(dragged: com.google.android.gms.maps.model.Marker) {
                if (dragged != marker) return
                val pos = dragged.position
                lifecycleScope.launch {
                    val resolved = GeocodeHelper.reverseGeocode(
                        this@ScheduleDeliveryActivity,
                        pos.latitude,
                        pos.longitude,
                    ) ?: GeocodeHelper.latLngString(pos)
                    binding.inputAddress.setText(resolved)
                    customerLat = pos.latitude
                    customerLng = pos.longitude
                    if (currentStep == 0) {
                        pickupLat = pos.latitude
                        pickupLng = pos.longitude
                    }
                }
            }
        })
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }

    private fun onNextClicked() {
        when (currentStep) {
            0, 1 -> {
                val address = binding.inputAddress.text.toString().trim()
                if (address.isEmpty()) {
                    Toast.makeText(this, R.string.schedule_address_required, Toast.LENGTH_SHORT).show()
                    return
                }
                if (currentStep == 0) {
                    pickupAddress = address
                    goToStep(1)
                } else {
                    dropoffAddress = address
                    goToStep(2)
                }
            }
            2 -> lifecycleScope.launch { submit() }
        }
    }

    private fun goToStep(step: Int) {
        if (step < 0) {
            finish()
            return
        }
        if (step > 2) return

        if (currentStep == 0 && step == 1) {
            pickupAddress = binding.inputAddress.text.toString().trim()
        }
        if (currentStep == 1 && step == 2) {
            dropoffAddress = binding.inputAddress.text.toString().trim()
        }

        currentStep = step
        updateStepUi()
    }

    private fun updateStepUi() {
        binding.stepProgress.progress = currentStep + 1
        binding.btnBack.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        binding.btnNext.text = if (currentStep == 2) getString(R.string.schedule_submit) else getString(R.string.schedule_next)

        val activeColor = ContextCompat.getColor(this, R.color.brand_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)
        binding.stepPickupLabel.setTextColor(if (currentStep == 0) activeColor else inactiveColor)
        binding.stepDropoffLabel.setTextColor(if (currentStep == 1) activeColor else inactiveColor)
        binding.stepDetailsLabel.setTextColor(if (currentStep == 2) activeColor else inactiveColor)
        listOf(binding.stepPickupLabel, binding.stepDropoffLabel, binding.stepDetailsLabel).forEach {
            it.setTypeface(it.typeface, if (it.currentTextColor == activeColor) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        when (currentStep) {
            0 -> {
                binding.stepHeading.setText(R.string.schedule_pickup_heading)
                binding.addressPanel.visibility = View.VISIBLE
                binding.detailsPanel.visibility = View.GONE
                binding.mapContainer.visibility = View.VISIBLE
                binding.inputAddress.setText(pickupAddress)
                marker?.remove()
                marker = null
                pickupLat?.let { lat ->
                    pickupLng?.let { lng -> placeMarker(LatLng(lat, lng)) }
                }
            }
            1 -> {
                binding.stepHeading.setText(R.string.schedule_dropoff_heading)
                binding.addressPanel.visibility = View.VISIBLE
                binding.detailsPanel.visibility = View.GONE
                binding.mapContainer.visibility = View.VISIBLE
                binding.inputAddress.setText(dropoffAddress)
                marker?.remove()
                marker = null
            }
            2 -> {
                binding.stepHeading.setText(R.string.schedule_details_heading)
                binding.addressPanel.visibility = View.GONE
                binding.detailsPanel.visibility = View.VISIBLE
                binding.mapContainer.visibility = View.GONE
                binding.reviewSummary.text = buildString {
                    append(getString(R.string.schedule_review_pickup, pickupAddress))
                    append("\n\n")
                    append(getString(R.string.schedule_review_dropoff, dropoffAddress))
                    val whenText = binding.inputWhen.text.toString().trim()
                    if (whenText.isNotEmpty()) {
                        append("\n\n")
                        append(getString(R.string.schedule_review_when, whenText))
                    }
                }
            }
        }
    }

    private suspend fun submit() {
        try {
            val uid = auth.currentUser?.uid ?: return
            val profile = db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).get().await()
            val fleetId = profile.getString("primaryFleetId")
            if (fleetId.isNullOrBlank()) {
                Toast.makeText(this, R.string.schedule_no_fleet, Toast.LENGTH_LONG).show()
                return
            }

            if (pickupAddress.isBlank() || dropoffAddress.isBlank()) {
                Toast.makeText(this, R.string.schedule_address_required, Toast.LENGTH_SHORT).show()
                return
            }

            val notes = binding.inputNotes.text.toString().trim()
            val whenText = binding.inputWhen.text.toString().trim()

            val data = hashMapOf<String, Any>(
                "customerUid" to uid,
                "pickupAddress" to pickupAddress,
                "dropoffAddress" to dropoffAddress,
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

            Toast.makeText(this, R.string.schedule_success, Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }
}
