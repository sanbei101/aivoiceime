package cn.sanbei101.aivoiceime.asr

import org.json.JSONObject
import java.nio.ByteBuffer

internal fun buildHeader(
    messageType: Int,
    flags: Int,
    serialization: Int = Serialization.JSON,
    compression: Int = Compression.GZIP
): ByteArray {
    return byteArrayOf(
        ((PROTOCOL_VERSION shl 4) or 1).toByte(),
        ((messageType shl 4) or flags).toByte(),
        ((serialization shl 4) or compression).toByte(),
        0x00
    )
}

internal fun buildFullClientRequest(uid: String = "android_uid"): ByteArray {
    val payload = JSONObject().apply {
        put("user", JSONObject().apply { put("uid", uid) })
        put("audio", JSONObject().apply {
            put("format", "pcm")
            put("codec", "raw")
            put("rate", 16000)
            put("bits", 16)
            put("channel", 1)
        })
        put("request", JSONObject().apply {
            put("model_name", "bigmodel")
            put("enable_itn", true)
            put("enable_punc", true)
            put("enable_ddc", true)
            put("show_utterances", true)
            put("enable_nonstream", false)
        })
    }.toString().toByteArray()

    val compressed = gzipCompress(payload)
    val header = buildHeader(MessageType.CLIENT_FULL_REQUEST, Flags.POS_SEQUENCE)

    return ByteBuffer.allocate(header.size + 4 + 4 + compressed.size).apply {
        put(header)
        putInt(1)
        putInt(compressed.size)
        put(compressed)
    }.array()
}

internal fun buildAudioRequest(seq: Int, segment: ByteArray): ByteArray {
    val flags = if (seq < 0) Flags.NEG_WITH_SEQUENCE else Flags.POS_SEQUENCE
    val header = buildHeader(MessageType.CLIENT_AUDIO_ONLY_REQUEST, flags)
    val compressed = gzipCompress(segment)

    return ByteBuffer.allocate(header.size + 4 + 4 + compressed.size).apply {
        put(header)
        putInt(seq)
        putInt(compressed.size)
        put(compressed)
    }.array()
}

internal fun buildAuthHeaders(apiKey: String, requestId: String): Map<String, String> = mapOf(
    "X-Api-Resource-Id" to "volc.seedasr.sauc.duration",
    "X-Api-Request-Id" to requestId,
    "X-Api-Key" to apiKey,
    "X-Api-Sequence" to "-1"
)