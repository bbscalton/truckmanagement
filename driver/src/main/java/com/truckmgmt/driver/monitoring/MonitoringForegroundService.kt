package com.truckmgmt.driver.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.truckmgmt.driver.DeviceLockActivity
import com.truckmgmt.driver.DriverHomeActivity
import com.truckmgmt.driver.R
import com.truckmgmt.driver.RingDeviceActivity
import com.truckmgmt.shared.TruckMgmtConstants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MonitoringForegroundService : Service() {
    private val db = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var commandListener: ListenerRegistration? = null
    private var lastMovingLocation: Location? = null
    private var lastMoveAt = 0L
    private var activeDeliveryId: String? = null
    private val io = Executors.newSingleThreadExecutor()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            publishLocation(loc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(TruckMgmtConstants.FGS_NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        listenCommands()
        resolveActiveDelivery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun fleetId() = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null)
    private fun deviceId() = prefs.getString(TruckMgmtConstants.PREF_DEVICE_ID, null)
    private fun driverId() = prefs.getString(TruckMgmtConstants.PREF_DRIVER_ID, null)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    TruckMgmtConstants.CHANNEL_MONITORING,
                    "Driver monitoring",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, DriverHomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, TruckMgmtConstants.CHANNEL_MONITORING)
            .setContentTitle(TruckMgmtConstants.DRIVER_LABEL)
            .setContentText("Location and activity reporting to dispatcher")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            TruckMgmtConstants.LOCATION_INTERVAL_MS,
        ).setMinUpdateIntervalMillis(30_000L).build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permissions not granted yet
        }
    }

    private fun resolveActiveDelivery() {
        val fid = fleetId() ?: return
        val did = driverId() ?: return
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DELIVERIES)
            .whereEqualTo("assignedDriverId", did)
            .addSnapshotListener { snap, _ ->
                activeDeliveryId = snap?.documents?.firstOrNull { d ->
                    val s = d.getString("status") ?: ""
                    s in setOf(
                        TruckMgmtConstants.STATUS_ACCEPTED_BY_DRIVER,
                        TruckMgmtConstants.STATUS_EN_ROUTE,
                        TruckMgmtConstants.STATUS_ARRIVED,
                        TruckMgmtConstants.STATUS_ASSIGNED,
                    )
                }?.id
            }
    }

    private fun publishLocation(loc: Location) {
        val fid = fleetId() ?: return
        val did = driverId() ?: return
        val now = System.currentTimeMillis()
        val moving = loc.speed > 1.5f || (lastMovingLocation?.let { loc.distanceTo(it) > 25f } == true)
        if (moving) {
            lastMovingLocation = loc
            lastMoveAt = now
        }
        val stopped = !moving && lastMoveAt > 0 && (now - lastMoveAt) >= TruckMgmtConstants.STOP_IDLE_MS

        val driverUpdates = hashMapOf<String, Any>(
            "lastLat" to loc.latitude,
            "lastLng" to loc.longitude,
            "lastAccuracy" to loc.accuracy,
            "lastSpeed" to loc.speed,
            "lastHeartbeatAt" to now,
            "online" to prefs.getBoolean(TruckMgmtConstants.PREF_ONLINE, true),
        )
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_DRIVERS).document(did)
            .update(driverUpdates)

        deviceId()?.let { devId ->
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_DEVICES).document(devId)
                .update(
                    mapOf(
                        "lastHeartbeatAt" to now,
                        "lastLat" to loc.latitude,
                        "lastLng" to loc.longitude,
                        "monitoringActive" to true,
                    )
                )
        }

        val trail = hashMapOf<String, Any>(
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "accuracyM" to loc.accuracy,
            "speedMps" to loc.speed,
            "ts" to now,
            "driverId" to did,
            "stopFlag" to stopped,
        )
        activeDeliveryId?.let { trail["deliveryId"] = it }
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_LOCATION_TRAIL)
            .add(trail)

        if (stopped) {
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_STOPS)
                .add(
                    mapOf(
                        "lat" to loc.latitude,
                        "lng" to loc.longitude,
                        "driverId" to did,
                        "deliveryId" to activeDeliveryId,
                        "startedAt" to now,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            // Reset so we don't spam stop docs every location tick
            lastMoveAt = now
        }

        syncEdge(fid, did, loc, now)
    }

    private fun syncEdge(fleetId: String, driverId: String, loc: Location, now: Long) {
        io.execute {
            try {
                val url = URL("${TruckMgmtConstants.R2_MEDIA_PROXY_BASE_URL}/edge/sync/device")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("content-type", "application/json")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val body = JSONObject()
                    .put("fleetId", fleetId)
                    .put("deviceId", deviceId() ?: driverId)
                    .put("driverName", prefs.getString("driver_name", "Driver"))
                    .put("lastHeartbeatMs", now)
                    .put("lat", loc.latitude)
                    .put("lng", loc.longitude)
                    .put("online", true)
                    .put("monitoringActive", true)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
                // Edge is best-effort; Firestore remains source of truth
            }
        }
    }

    private fun listenCommands() {
        val fid = fleetId() ?: return
        val did = driverId() ?: return
        commandListener?.remove()
        commandListener = db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_COMMANDS)
            .whereEqualTo("targetDriverId", did)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    val doc = change.document
                    when (doc.getString("type")) {
                        "LOCK_DEVICE" -> {
                            startActivity(
                                Intent(this, DeviceLockActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            doc.reference.update("status", "done")
                        }
                        "UNLOCK_DEVICE" -> {
                            sendBroadcast(Intent(TruckMgmtConstants.ACTION_DEVICE_UNLOCK))
                            doc.reference.update("status", "done")
                        }
                        "RING_DEVICE" -> {
                            startActivity(
                                Intent(this, RingDeviceActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            doc.reference.update("status", "done")
                        }
                        "REFRESH_LOCATION" -> {
                            fused.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) publishLocation(loc)
                            }
                            doc.reference.update("status", "done")
                        }
                    }
                }
            }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(locationCallback)
        commandListener?.remove()
        super.onDestroy()
    }
}
