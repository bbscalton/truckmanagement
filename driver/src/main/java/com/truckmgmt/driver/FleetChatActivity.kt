package com.truckmgmt.driver

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.chat.BaseChatActivity

class FleetChatActivity : BaseChatActivity() {
    private val prefs by lazy { getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE) }
    private var fleetId: String? = null

    override fun chatTitle() = "Fleet chat"

    override fun senderRole() = "driver"

    override suspend fun ensureReady() {
        fleetId = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null)
            ?: throw IllegalStateException("Not paired")
    }

    override fun messagesCollection(): CollectionReference {
        val fid = fleetId ?: throw IllegalStateException("Fleet not loaded")
        return db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_FLEET_CHAT)
    }

    override suspend fun fleetIdForUpload(): String? = fleetId

    override fun onMessageSent(type: String, text: String) {
        val fid = fleetId ?: return
        if (type == TruckMgmtConstants.MSG_TYPE_TEXT && text.isNotBlank()) {
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_ACTIVITY_LOGS)
                .add(
                    mapOf(
                        "type" to "chat",
                        "summary" to text,
                        "driverId" to prefs.getString(TruckMgmtConstants.PREF_DRIVER_ID, null),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "ts" to System.currentTimeMillis(),
                    ),
                )
        }
    }
}
