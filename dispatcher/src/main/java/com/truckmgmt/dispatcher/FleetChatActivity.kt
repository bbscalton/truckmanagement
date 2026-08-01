package com.truckmgmt.dispatcher

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FleetChatActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var fleetId: String? = null
    private lateinit var messagesView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        messagesView = TextView(this)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(messagesView)
        }
        val input = EditText(this).apply { hint = "Message drivers…" }
        val send = Button(this).apply { text = "Send" }
        root.addView(scroll)
        root.addView(input)
        root.addView(send)
        setContentView(root)

        lifecycleScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val profile = db.collection(TruckMgmtConstants.COL_DISPATCHER_PROFILES).document(uid).get().await()
            fleetId = profile.getString("primaryFleetId")
            listen()
        }

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val fid = fleetId ?: return@setOnClickListener
            db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
                .collection(TruckMgmtConstants.COL_FLEET_CHAT)
                .add(
                    mapOf(
                        "text" to text,
                        "senderUid" to auth.currentUser?.uid,
                        "senderRole" to "dispatcher",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            input.text.clear()
        }
    }

    private fun listen() {
        val fid = fleetId ?: return
        db.collection(TruckMgmtConstants.COL_FLEETS).document(fid)
            .collection(TruckMgmtConstants.COL_FLEET_CHAT)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                messagesView.text = snap?.documents?.joinToString("\n") {
                    "[${it.getString("senderRole")}] ${it.getString("text")}"
                } ?: ""
            }
    }
}
