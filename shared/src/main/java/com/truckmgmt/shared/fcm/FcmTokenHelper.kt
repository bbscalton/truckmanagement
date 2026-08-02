package com.truckmgmt.shared.fcm

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FcmTokenHelper {

    suspend fun registerDispatcherToken(uid: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection(TruckMgmtConstants.COL_DISPATCHER_PROFILES)
            .document(uid)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "fcmUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    suspend fun registerCustomerToken(uid: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES)
            .document(uid)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "fcmUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    suspend fun registerDriverDeviceToken(fleetId: String, deviceId: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
            .collection(TruckMgmtConstants.COL_DEVICES).document(deviceId)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "fcmUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    fun refreshToken(context: Context, role: Role) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (role) {
                        Role.DISPATCHER -> registerDispatcherToken(uid, token)
                        Role.CUSTOMER -> registerCustomerToken(uid, token)
                        Role.DRIVER -> {
                            val prefs = context.getSharedPreferences(TruckMgmtConstants.PREFS_NAME, Context.MODE_PRIVATE)
                            val fleetId = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null) ?: return@launch
                            val deviceId = prefs.getString(TruckMgmtConstants.PREF_DEVICE_ID, null) ?: return@launch
                            registerDriverDeviceToken(fleetId, deviceId, token)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    enum class Role { DISPATCHER, DRIVER, CUSTOMER }
}
