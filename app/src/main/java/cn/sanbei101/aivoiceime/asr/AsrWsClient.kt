package cn.sanbei101.aivoiceime.asr

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val SEGMENT_BYTES = 16000 * 2 * 200 / 1000

class AsrWsClient(private val url: String, private val apiKey: String) {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun startSession(uid: String = "android_uid") = AsrSession(httpClient, url, apiKey, uid)
}

class AsrSession internal constructor(
    private val httpClient: OkHttpClient,
    private val url: String,
    private val apiKey: String,
    private val uid: String
) {
    private val lock = Any()
    @Volatile private var handshakeDone = false
    @Volatile private var ws: WebSocket? = null
    private val segmentBuffer = ByteArray(SEGMENT_BYTES)
    private var segmentBytes = 0
    private val pending = ByteArrayOutputStream()

    val responses: Flow<AsrResponse> = callbackFlow {
        val headers = buildAuthHeaders(apiKey, UUID.randomUUID().toString())
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        val conn = httpClient.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("AsrSession", "connected logid=${response.header("X-Tt-Logid")}")
                webSocket.send(buildFullClientRequest(uid).toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val resp = parseResponse(bytes.toByteArray())
                if (!handshakeDone) {
                    handshakeDone = true
                    if (resp.code != 0) {
                        close(RuntimeException("Handshake error: ${resp.code}"))
                        webSocket.close(1000, null)
                        return
                    }
                    synchronized(lock) {
                        if (pending.size() > 0) {
                            drainLocked(pending.toByteArray())
                            pending.reset()
                        }
                    }
                    return
                }
                trySend(resp)
                if (resp.isLastPackage || resp.code != 0) {
                    close()
                    webSocket.close(1000, null)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("AsrSession", "failure: code=${response?.code}", t)
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        ws = conn
        awaitClose { conn.close(1000, null) }
    }

    fun sendPcm(pcm: ByteArray) {
        sendPcm(pcm, 0, pcm.size)
    }

    fun sendPcm(pcm: ByteArray, offset: Int, length: Int) = synchronized(lock) {
        if (!handshakeDone) {
            pending.write(pcm, offset, length)
        } else {
            drainLocked(pcm, offset, length)
        }
    }

    fun finish() = synchronized(lock) {
        ws?.send(buildAudioRequest(true, segmentBuffer, 0, segmentBytes).toByteString())
        segmentBytes = 0
    }

    private fun drainLocked(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size - offset) {
        var readOffset = offset
        var remaining = length
        while (remaining > 0) {
            val bytesToCopy = min(remaining, SEGMENT_BYTES - segmentBytes)
            pcm.copyInto(
                destination = segmentBuffer,
                destinationOffset = segmentBytes,
                startIndex = readOffset,
                endIndex = readOffset + bytesToCopy
            )
            segmentBytes += bytesToCopy
            readOffset += bytesToCopy
            remaining -= bytesToCopy

            if (segmentBytes == SEGMENT_BYTES) {
                ws?.send(buildAudioRequest(false, segmentBuffer).toByteString())
                segmentBytes = 0
            }
        }
    }
}
