package cn.sanbei101.aivoiceime.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.UUID

class AsrWsClient(
    private val url: String,
    private val appKey: String,
    private val accessKey: String,
    private val segmentDurationMs: Int = 200
) {
    private val httpClient = OkHttpClient()

    fun recognize(audioData: ByteArray, uid: String = "android_uid"): Flow<AsrResponse> {
        require(isWav(audioData)) { "Only WAV format is supported" }
        val wavInfo = readWavInfo(audioData)
        val segmentSize = wavInfo.channels * wavInfo.sampleWidth * wavInfo.frameRate * segmentDurationMs / 1000
        val segments = wavInfo.audioData.toList().chunked(segmentSize).map { it.toByteArray() }

        return callbackFlow {
            val headers = buildAuthHeaders(appKey, accessKey, UUID.randomUUID().toString())
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            var seq = 1
            var handshakeDone = false

            val ws = httpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(buildFullClientRequest(uid).toByteString())
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val resp = parseResponse(bytes.toByteArray())

                    if (!handshakeDone) {
                        handshakeDone = true
                        if (resp.code != 0) {
                            close(RuntimeException("Handshake failed: code=${resp.code}"))
                            webSocket.close(1000, null)
                            return
                        }
                        launch(Dispatchers.IO) {
                            segments.forEachIndexed { index, segment ->
                                val s = if (index == segments.lastIndex) -seq else seq
                                webSocket.send(buildAudioRequest(s, segment).toByteString())
                                seq++
                                delay(segmentDurationMs.toLong())
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
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            })

            awaitClose { ws.close(1000, null) }
        }
    }
}
