package com.truckmgmt.dispatcher

import com.google.firebase.firestore.CollectionReference
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.chat.BaseChatActivity
import kotlinx.coroutines.tasks.await

class FleetChatActivity : BaseChatActivity() {
    private var fleetId: String? = null

    override fun chatTitle() = "Fleet chat"

    override fun senderRole() = "dispatcher"

    override suspend fun ensureReady() {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val profile = db.collection(TruckMgmtConstants.COL_DISPATCHER_PROFILES).document(uid).get().await()
        fleetId = profile.getString("primaryFleetId")
            ?: throw IllegalStateException("No fleet linked")
    }

    override fun messagesCollection(): CollectionReference {
        val fid = fleetId ?: throw IllegalStateException("Fleet not loaded")
        return db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_FLEET_CHAT)
    }

    override suspend fun fleetIdForUpload(): String? = fleetId
}
