package com.truckmgmt.driver

import com.google.firebase.firestore.CollectionReference
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.chat.BaseChatActivity

class TripChatActivity : BaseChatActivity() {
    private var fleetId: String? = null
    private var deliveryId: String? = null
    private val prefs by lazy { getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE) }

    override fun chatTitle() = "Trip chat"

    override fun senderRole() = "driver"

    override suspend fun ensureReady() {
        deliveryId = intent.getStringExtra("deliveryId")
            ?: throw IllegalStateException("Missing deliveryId")
        fleetId = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null)
            ?: throw IllegalStateException("Not paired")
    }

    override fun messagesCollection(): CollectionReference {
        val fid = fleetId ?: throw IllegalStateException("Fleet not loaded")
        val did = deliveryId ?: throw IllegalStateException("Delivery not set")
        return db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_TRIP_CHAT).document(did)
            .collection("messages")
    }

    override suspend fun fleetIdForUpload(): String? = fleetId
}
