package com.truckmgmt.customer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TripChatActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deliveryId = intent.getStringExtra("deliveryId") ?: run { finish(); return }
        val messagesView = TextView(this)
        val input = EditText(this).apply { hint = "Message…" }
        val send = Button(this).apply { text = "Send" }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(ScrollView(this@TripChatActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                addView(messagesView)
            })
            addView(input)
            addView(send)
        }
        setContentView(root)

        CoroutineScope(Dispatchers.Main).launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val profile = db.collection(TruckMgmtConstants.COL_CUSTOMER_PROFILES).document(uid).get().await()
            val fleetId = profile.getString("primaryFleetId") ?: return@launch

            db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                .collection(TruckMgmtConstants.COL_TRIP_CHAT).document(deliveryId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snap, _ ->
                    messagesView.text = snap?.documents?.joinToString("\n") {
                        "[${it.getString("senderRole")}] ${it.getString("text")}"
                    } ?: ""
                }

            send.setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener
                db.collection(TruckMgmtConstants.COL_FLEETS).document(fleetId)
                    .collection(TruckMgmtConstants.COL_TRIP_CHAT).document(deliveryId)
                    .collection("messages")
                    .add(
                        mapOf(
                            "text" to text,
                            "senderUid" to uid,
                            "senderRole" to "customer",
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                input.text.clear()
            }
        }
    }
}
