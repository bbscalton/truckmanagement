package com.truckmgmt.shared.chat

import android.graphics.Color
import android.media.MediaPlayer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.truckmgmt.shared.R
import com.truckmgmt.shared.TruckMgmtConstants

class ChatMessageAdapter(
    private val myRole: String,
) : ListAdapter<ChatMessage, ChatMessageAdapter.VH>(DIFF) {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), myRole) { url ->
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepare()
                start()
            }
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubbleRoot: LinearLayout = itemView.findViewById(R.id.bubbleRoot)
        private val senderLabel: TextView = itemView.findViewById(R.id.senderLabel)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage)
        private val audioRow: LinearLayout = itemView.findViewById(R.id.audioRow)
        private val btnPlayAudio: ImageButton = itemView.findViewById(R.id.btnPlayAudio)
        private val audioDuration: TextView = itemView.findViewById(R.id.audioDuration)
        private val timeLabel: TextView = itemView.findViewById(R.id.timeLabel)

        fun bind(msg: ChatMessage, myRole: String, onPlay: (String) -> Unit) {
            val outgoing = msg.senderRole == myRole
            val lp = bubbleRoot.layoutParams as FrameLayout.LayoutParams
            lp.gravity = if (outgoing) Gravity.END else Gravity.START
            bubbleRoot.layoutParams = lp
            bubbleRoot.setBackgroundResource(
                if (outgoing) R.drawable.chat_bubble_outgoing else R.drawable.chat_bubble_incoming,
            )
            senderLabel.text = msg.senderRole
            senderLabel.setTextColor(if (outgoing) Color.parseColor("#B8C9E0") else Color.parseColor("#667788"))
            timeLabel.text = msg.formattedTime()
            timeLabel.setTextColor(if (outgoing) Color.parseColor("#B8C9E0") else Color.parseColor("#8899AA"))

            messageImage.visibility = View.GONE
            audioRow.visibility = View.GONE
            messageText.visibility = View.GONE

            when (msg.type) {
                TruckMgmtConstants.MSG_TYPE_IMAGE -> {
                    messageImage.visibility = View.VISIBLE
                    messageImage.load(msg.mediaUrl)
                    if (msg.text.isNotBlank()) {
                        messageText.visibility = View.VISIBLE
                        messageText.text = msg.text
                        messageText.setTextColor(if (outgoing) Color.WHITE else Color.parseColor("#1A2332"))
                    }
                }
                TruckMgmtConstants.MSG_TYPE_AUDIO -> {
                    audioRow.visibility = View.VISIBLE
                    val sec = msg.audioDurationSec ?: 0
                    audioDuration.text = if (sec > 0) "${sec}s voice note" else "Voice note"
                    audioDuration.setTextColor(if (outgoing) Color.WHITE else Color.parseColor("#334455"))
                    btnPlayAudio.setOnClickListener { msg.mediaUrl?.let(onPlay) }
                    if (msg.text.isNotBlank()) {
                        messageText.visibility = View.VISIBLE
                        messageText.text = msg.text
                        messageText.setTextColor(if (outgoing) Color.WHITE else Color.parseColor("#1A2332"))
                    }
                }
                else -> {
                    messageText.visibility = View.VISIBLE
                    messageText.text = msg.text
                    messageText.setTextColor(if (outgoing) Color.WHITE else Color.parseColor("#1A2332"))
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
            override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
        }
    }
}
