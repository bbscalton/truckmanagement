package com.truckmgmt.shared.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.truckmgmt.shared.R
import com.truckmgmt.shared.TruckMgmtConstants
import com.truckmgmt.shared.media.R2Uploader
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

abstract class BaseChatActivity : AppCompatActivity() {

    protected val auth = FirebaseAuth.getInstance()
    protected val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private var adapter: ChatMessageAdapter? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAt = 0L

    abstract fun chatTitle(): String
    abstract fun senderRole(): String
    abstract fun messagesCollection(): CollectionReference
    abstract suspend fun ensureReady()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val fleetId = fleetIdForUpload() ?: return@launch
                val url = R2Uploader.uploadBytes(fleetId, bytes, "image/jpeg", "jpg")
                sendMessage(TruckMgmtConstants.MSG_TYPE_IMAGE, "", url)
            } catch (e: Exception) {
                Toast.makeText(this@BaseChatActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) toggleRecording() else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base_chat)
        title = chatTitle()

        val recycler = findViewById<RecyclerView>(R.id.chatRecycler)
        val empty = findViewById<TextView>(R.id.chatEmpty)
        val input = findViewById<EditText>(R.id.chatInput)
        val sendBtn = findViewById<Button>(R.id.btnSend)
        val attachBtn = findViewById<ImageButton>(R.id.btnAttachImage)
        val recordBtn = findViewById<ImageButton>(R.id.btnRecordVoice)
        val recordingLabel = findViewById<TextView>(R.id.recordingLabel)

        adapter = ChatMessageAdapter(senderRole()).also {
            recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
            recycler.adapter = it
        }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                sendMessage(TruckMgmtConstants.MSG_TYPE_TEXT, text, null)
                input.text.clear()
            }
        }

        attachBtn.setOnClickListener { pickImage.launch("image/*") }

        recordBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestMic.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                toggleRecording(recordingLabel)
            }
        }

        lifecycleScope.launch {
            ensureReady()
            attachListener(recycler, empty)
        }
    }

    private fun attachListener(recycler: RecyclerView, empty: TextView) {
        listener = messagesCollection()
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(200)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val msgs = snap?.documents?.map { ChatMessage.fromDoc(it, senderRole()) } ?: emptyList()
                adapter?.submitList(msgs) {
                    if (msgs.isNotEmpty()) recycler.scrollToPosition(msgs.size - 1)
                }
                empty.visibility = if (msgs.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (msgs.isEmpty()) View.GONE else View.VISIBLE
            }
    }

    private fun toggleRecording(label: TextView? = findViewById(R.id.recordingLabel)) {
        if (recorder != null) {
            stopRecordingAndSend(label)
        } else {
            startRecording(label)
        }
    }

    private fun startRecording(label: TextView?) {
        recordingFile = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(recordingFile!!.absolutePath)
            prepare()
            start()
        }
        recordingStartedAt = System.currentTimeMillis()
        label?.visibility = View.VISIBLE
    }

    private fun stopRecordingAndSend(label: TextView?) {
        val file = recordingFile
        val durationSec = ((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt().coerceAtLeast(1)
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
        label?.visibility = View.GONE
        if (file == null || !file.exists()) return
        lifecycleScope.launch {
            try {
                val fleetId = fleetIdForUpload() ?: return@launch
                val url = R2Uploader.uploadFile(fleetId, file, "audio/mp4", "m4a")
                sendMessage(TruckMgmtConstants.MSG_TYPE_AUDIO, "", url, durationSec)
            } catch (e: Exception) {
                Toast.makeText(this@BaseChatActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                file.delete()
            }
        }
    }

    protected open suspend fun fleetIdForUpload(): String? = null

    protected suspend fun sendMessage(type: String, text: String, mediaUrl: String?, audioDurationSec: Int? = null) {
        val payload = buildMap<String, Any> {
            put("type", type)
            put("text", text)
            put("senderUid", auth.currentUser?.uid ?: "")
            put("senderRole", senderRole())
            put("createdAt", FieldValue.serverTimestamp())
            if (mediaUrl != null) put("mediaUrl", mediaUrl)
            if (audioDurationSec != null) put("audioDurationSec", audioDurationSec)
        }
        messagesCollection().add(payload).await()
        onMessageSent(type, text)
    }

    protected open fun onMessageSent(type: String, text: String) {}

    override fun onDestroy() {
        listener?.remove()
        recorder?.release()
        super.onDestroy()
    }
}
