package com.truckmgmt.customer

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.truckmgmt.shared.FleetIdGenerator
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.tasks.await

class FleetNotFoundException(fleetId: String) : Exception("Fleet \"$fleetId\" not found. Check the 6-character code from your dispatcher.")

class FleetLinkPermissionException : Exception(
    "Could not link to fleet (permission denied). Try again or contact your dispatcher.",
)

object FleetLinkHelper {
    suspend fun linkCustomerToFleet(
        db: FirebaseFirestore,
        uid: String,
        email: String,
        fleetIdRaw: String,
        displayName: String? = null,
    ): String {
        val fleetId = FleetIdGenerator.normalize(fleetIdRaw)
        if (fleetId.isBlank()) throw IllegalArgumentException("Fleet ID is required")

        val fleetSnap = try {
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId).get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw FleetLinkPermissionException()
            }
            throw e
        }
        if (!fleetSnap.exists()) throw FleetNotFoundException(fleetId)

        val name = displayName ?: email.substringBefore("@")
        try {
            db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).set(
                mapOf(
                    "email" to email,
                    "displayName" to name,
                    "fleetIds" to listOf(fleetId),
                    "primaryFleetId" to fleetId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()

            db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                .collection(TruckMgmtConstants.COL_CUSTOMERS).document(uid)
                .set(
                    mapOf(
                        "email" to email,
                        "displayName" to name,
                        "linkedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw FleetLinkPermissionException()
            }
            throw e
        }

        return fleetId
    }
}
