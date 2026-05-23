package cn.sanbei101.aivoiceime.asr

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
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val SEGMENT_SAMPLES = 16000 * 200 / 1000  // 200ms @ 16kHz mono 16bit
private const val SEGMENT_BYTES = SEGMENT_SAMPLES * 2

class AsrWsClient(
    private val url: String,
    private val apiKey: String
) {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun startSession(uid: String = "android_uid"): AsrSession {
        return AsrSession(httpClient, url, apiKey, uid)
    }
}

class AsrSession internal constructor(
    httpClient: OkHttpClient,
    url: String,
    apiKey: String,
    uid: String
) {
    private var seq = 1
    @Volatile private var handshakeDone = false
    private val pcmAccumulator = mutableListOf<Byte>()
    private val pendingPcm = mutableListOf<Byte>()  // buffered before handshake

    val responses: Flow<AsrResponse>
    private lateinit var ws: WebSocket
    private lateinit var flowClose: (Throwable?) -> Unit

    init {
        val headers = buildAuthHeaders(apiKey, UUID.randomUUID().toString())
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        responses = callbackFlow {
            flowClose = { err -> if (err != null) close(err) else close() }

            ws = httpClient.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(buildFullClientRequest(uid).toByteString())
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val resp = parseResponse(bytes.toByteArray())
                    if (!handshakeDone) {
                        handshakeDone = true
                        if (resp.code != 0) {
                            flowClose(RuntimeException("Handshake error: ${resp.code}"))
                            return
                        }
                        // flush buffered PCM collected before handshake completed
                        synchronized(pendingPcm) {
                            if (pendingPcm.isNotEmpty()) {
                                drainPcm(pendingPcm.toByteArray(), false)
                                pendingPcm.clear()
                            }
                        }
                        return
                    }
                    trySend(resp)
                    if (resp.isLastPackage || resp.code != 0) flowClose(null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    android.util.Log.e("AsrSession", "WebSocket failure: ${response?.code} ${t.message}")
                    flowClose(null)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = flowClose(null)
            })

            awaitClose { ws.close(1000, null) }
        }
    }

    fun sendPcm(pcm: ByteArray) {
        if (!handshakeDone) {
            synchronized(pendingPcm) { pendingPcm.addAll(pcm.toList()) }
            return
        }
        drainPcm(pcm, false)
    }

    fun finish() {
        val remaining: ByteArray
        synchronized(pendingPcm) {
            remaining = (pendingPcm + pcmAccumulator).toByteArray()
            pendingPcm.clear()
            pcmAccumulator.clear()
        }
        ws.send(buildAudioRequest(-seq, remaining).toByteString())
    }

    private fun drainPcm(pcm: ByteArray, isFinal: Boolean) {
        pcmAccumulator.addAll(pcm.toList())
        while (pcmAccumulator.size >= SEGMENT_BYTES) {
            val chunk = pcmAccumulator.subList(0, SEGMENT_BYTES).toByteArray()
            pcmAccumulator.subList(0, SEGMENT_BYTES).clear()
            ws.send(buildAudioRequest(seq++, chunk).toByteString())
        }
    }
}