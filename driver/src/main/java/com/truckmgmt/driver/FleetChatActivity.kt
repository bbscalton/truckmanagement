package com.truckmgmt.driver

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

class FleetChatActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences(TruckMgmtConstants.PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val messagesView = TextView(this)
        val input = EditText(this).apply { hint = "Message dispatcher…" }
        val send = Button(this).apply { text = "Send" }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(ScrollView(this@FleetChatActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                addView(messagesView)
            })
            addView(input)
            addView(send)
        }
        setContentView(root)

        val fid = prefs.getString(TruckMgmtConstants.PREF_FLEET_ID, null) ?: return
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_FLEET_CHAT)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                messagesView.text = snap?.documents?.joinToString("\n") {
                    "[${it.getString("senderRole")}] ${it.getString("text")}"
                } ?: ""
            }

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_FLEET_CHAT)
                .add(
                    mapOf(
                        "text" to text,
                        "senderUid" to auth.currentUser?.uid,
                        "senderRole" to "driver",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            // Mirror into monitored activity for dispatcher
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_ACTIVITY_LOGS)
                .add(
                    mapOf(
                        "type" to "chat",
                        "summary" to text,
                        "driverId" to prefs.getString(TruckMgmtConstants.PREF_DRIVER_ID, null),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "ts" to System.currentTimeMillis(),
                    )
                )
            input.text.clear()
        }
    }
}
