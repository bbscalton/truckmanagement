package com.truckmgmt.dispatcher

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.truckmgmt.shared.FleetIdGenerator
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.tasks.await

object FleetCreateHelper {
    suspend fun createFleetWithShortId(
        db: FirebaseFirestore,
        ownerUid: String,
        name: String,
        maxAttempts: Int = 10,
    ): String {
        repeat(maxAttempts) {
            val id = FleetIdGenerator.generate()
            val ref = db.collection(TruckMgmtConstants.COL_FLEETS).document(id)
            if (ref.get().await().exists()) return@repeat
            ref.set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "name" to name,
                    "shortCode" to id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "tripCount" to 0,
                    "totalRevenue" to 0.0,
                ),
            ).await()
            return id
        }
        throw IllegalStateException("Could not allocate a fleet ID. Please try again.")
    }
}
