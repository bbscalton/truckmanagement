package com.truckmgmt.shared.chat

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.truckmgmt.shared.TruckMgmtConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val id: String,
    val type: String,
    val text: String,
    val mediaUrl: String?,
    val senderRole: String,
    val senderUid: String?,
    val createdAt: Date?,
    val audioDurationSec: Int? = null,
) {
    val isOutgoing: Boolean get() = senderRole == myRole
    var myRole: String = "unknown"

    fun formattedTime(): String {
        val d = createdAt ?: return ""
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(d)
    }

    companion object {
        fun fromDoc(doc: DocumentSnapshot, myRole: String): ChatMessage {
            val ts = doc.getTimestamp("createdAt")
            return ChatMessage(
                id = doc.id,
                type = doc.getString("type") ?: TruckMgmtConstants.MSG_TYPE_TEXT,
                text = doc.getString("text") ?: "",
                mediaUrl = doc.getString("mediaUrl"),
                senderRole = doc.getString("senderRole") ?: "unknown",
                senderUid = doc.getString("senderUid"),
                createdAt = ts?.toDate(),
                audioDurationSec = doc.getLong("audioDurationSec")?.toInt(),
            ).apply { this.myRole = myRole }
        }
    }
}
