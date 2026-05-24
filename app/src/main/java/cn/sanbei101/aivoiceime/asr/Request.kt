package cn.sanbei101.aivoiceime.asr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

private val requestJson = Json {
    encodeDefaults = true
}

@Serializable
private data class FullClientPayload(
    val user: UserPayload,
    val audio: AudioPayload,
    val request: RequestPayload
)

@Serializable
private data class UserPayload(
    val uid: String
)

@Serializable
private data class AudioPayload(
    val format: String = "pcm",
    val codec: String = "raw",
    val rate: Int = 16000,
    val bits: Int = 16,
    val channel: Int = 1
)

@Serializable
private data class RequestPayload(
    @SerialName("model_name") val modelName: String = "bigmodel",
    @SerialName("enable_itn") val enableItn: Boolean = true,
    @SerialName("enable_punc") val enablePunc: Boolean = true,
    @SerialName("enable_ddc") val enableDdc: Boolean = true,
    @SerialName("show_utterances") val showUtterances: Boolean = true,
    @SerialName("enable_nonstream") val enableNonstream: Boolean = true,
    @SerialName("end_window_size") val endWindowSize: Int = 200
)

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
    val payload = requestJson.encodeToString(
        FullClientPayload(
            user = UserPayload(uid),
            audio = AudioPayload(),
            request = RequestPayload()
        )
    ).toByteArray()

    val compressed = gzipCompress(payload)
    val header = buildHeader(MessageType.CLIENT_FULL_REQUEST, Flags.NO_SEQUENCE)

    return ByteBuffer.allocate(header.size + 4 + compressed.size).apply {
        put(header)
        putInt(compressed.size)
        put(compressed)
    }.array()
}

internal fun buildAudioRequest(
    isLast: Boolean,
    segment: ByteArray,
    offset: Int = 0,
    length: Int = segment.size - offset
): ByteArray {
    val flags = if (isLast) Flags.NEG_SEQUENCE else Flags.NO_SEQUENCE
    val header = buildHeader(MessageType.CLIENT_AUDIO_ONLY_REQUEST, flags, Serialization.NO_SERIALIZATION)
    val compressed = gzipCompress(segment, offset, length)

    return ByteBuffer.allocate(header.size + 4 + compressed.size).apply {
        put(header)
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
