package com.truckmgmt.shared

object TruckMgmtConstants {
    const val APP_NAME = "TruckMgmt"
    const val DRIVER_LABEL = "Company device — TruckMgmt Driver"

    const val R2_MEDIA_PROXY_BASE_URL = "https://truckmgmt-media-proxy.neuereatec.workers.dev"
    const val R2_UPLOAD_PATH = "/upload/"
    const val R2_MEDIA_PATH = "/media/"
    const val R2_DOWNLOADS_PATH = "/downloads/"

    /** PUT {R2_MEDIA_PROXY_BASE_URL}/upload/{key} — upload blob to R2 via Worker. */
    fun r2UploadUrl(objectKey: String) = "$R2_MEDIA_PROXY_BASE_URL$R2_UPLOAD_PATH$objectKey"

    /** GET {R2_MEDIA_PROXY_BASE_URL}/media/{key} — public media URL. */
    fun r2MediaUrl(objectKey: String) = "$R2_MEDIA_PROXY_BASE_URL$R2_MEDIA_PATH$objectKey"

    /** GET {R2_MEDIA_PROXY_BASE_URL}/downloads/{key} — APK/binary download (key usually under downloads/). */
    fun r2DownloadUrl(objectKey: String) = "$R2_MEDIA_PROXY_BASE_URL$R2_DOWNLOADS_PATH$objectKey"

    // Top-level collections
    const val COL_DISPATCHER_PROFILES = "dispatcherProfiles"
    const val COL_CUSTOMER_PROFILES = "customerProfiles"
    const val COL_FLEETS = "fleets"
    const val COL_PAIRING_CODES = "pairingCodes"
    const val COL_INVITE_CODES = "inviteCodes"

    // Under fleets/{fleetId}
    const val COL_TRUCKS = "trucks"
    const val COL_DRIVERS = "drivers"
    const val COL_DEVICES = "devices"
    const val COL_CUSTOMERS = "customers"
    const val COL_DELIVERIES = "deliveries"
    const val COL_DELIVERY_REQUESTS = "deliveryRequests"
    const val COL_LOCATION_TRAIL = "locationTrail"
    const val COL_TRIPS = "trips"
    const val COL_PAYMENTS = "payments"
    const val COL_FLEET_CHAT = "fleetChat"
    const val COL_TRIP_CHAT = "tripChat"
    const val COL_COMMANDS = "commands"
    const val COL_STOPS = "stops"
    const val COL_ACTIVITY_LOGS = "activityLogs"

    // Delivery statuses
    const val STATUS_REQUESTED = "requested"
    const val STATUS_DISPATCHER_REVIEW = "dispatcher_review"
    const val STATUS_AUTO_NEAREST = "auto_nearest"
    const val STATUS_ASSIGNED = "assigned"
    const val STATUS_ACCEPTED_BY_DRIVER = "accepted_by_driver"
    const val STATUS_EN_ROUTE = "en_route"
    const val STATUS_ARRIVED = "arrived"
    const val STATUS_DELIVERED = "delivered"
    const val STATUS_PAYMENT_PENDING = "payment_pending"
    const val STATUS_PAYMENT_ACCEPTED = "payment_accepted"
    const val STATUS_CLOSED = "closed"
    const val STATUS_CANCELLED = "cancelled"
    const val STATUS_REJECTED = "rejected"

    // Timing
    const val HEARTBEAT_INTERVAL_MS = 60_000L
    const val LOCATION_INTERVAL_MS = 120_000L
    const val WENT_DARK_AFTER_MS = 5 * 60_000L
    const val STOP_IDLE_MS = 3 * 60_000L
    const val NEAREST_DRIVER_TIMEOUT_MS = 2 * 60_000L
    const val PAIRING_CODE_TTL_MS = 30 * 60_000L

    // Prefs
    const val PREFS_NAME = "truckmgmt_prefs"
    const val PREF_FLEET_ID = "fleet_id"
    const val PREF_DEVICE_ID = "device_id"
    const val PREF_DRIVER_ID = "driver_id"
    const val PREF_TRUCK_ID = "truck_id"
    const val PREF_PERMISSIONS_GRANTED = "permissions_granted"
    const val PREF_ONLINE = "online"

    // Notifications
    const val CHANNEL_MONITORING = "truckmgmt_monitoring"
    const val CHANNEL_JOBS = "truckmgmt_jobs"
    const val CHANNEL_CHAT = "truckmgmt_chat"
    const val FGS_NOTIFICATION_ID = 7101

    const val ACTION_DEVICE_UNLOCK = "com.truckmgmt.driver.ACTION_DEVICE_UNLOCK"
    const val ACTION_STOP_RING = "com.truckmgmt.driver.ACTION_STOP_RING"
}
