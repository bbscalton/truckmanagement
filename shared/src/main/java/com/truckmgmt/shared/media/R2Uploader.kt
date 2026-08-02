package com.truckmgmt.shared.media

import com.truckmgmt.shared.TruckMgmtConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object R2Uploader {

    suspend fun uploadBytes(
        fleetId: String,
        bytes: ByteArray,
        contentType: String,
        extension: String,
    ): String = withContext(Dispatchers.IO) {
        val key = "fleets/$fleetId/chat/${UUID.randomUUID()}.$extension"
        val url = URL(TruckMgmtConstants.r2UploadUrl(key))
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", contentType)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("Upload failed: HTTP $code")
        TruckMgmtConstants.r2MediaUrl(key)
    }

    suspend fun uploadFile(
        fleetId: String,
        file: File,
        contentType: String,
        extension: String,
    ): String = uploadBytes(fleetId, file.readBytes(), contentType, extension)
}
