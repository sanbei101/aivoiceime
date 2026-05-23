package cn.sanbei101.aivoiceime.asr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

private val responseJson = Json {
    ignoreUnknownKeys = true
}

data class AsrWord(val text: String, val startTime: Int, val endTime: Int)

data class AsrUtterance(
    val text: String,
    val startTime: Int,
    val endTime: Int,
    val definite: Boolean,
    val words: List<AsrWord>
)

data class AsrResult(
    val text: String,
    val utterances: List<AsrUtterance>,
    val audioDuration: Int
)

data class AsrResponse(
    val code: Int,
    val isLastPackage: Boolean,
    val payloadSequence: Int,
    val result: AsrResult?,
    val error: String?
)

@Serializable
private data class AsrResponsePayload(
    val error: String? = null,
    val result: AsrResultPayload? = null,
    @SerialName("audio_info") val audioInfo: AudioInfoPayload? = null
)

@Serializable
private data class AsrResultPayload(
    val text: String = "",
    val utterances: List<AsrUtterancePayload> = emptyList()
)

@Serializable
private data class AsrUtterancePayload(
    val text: String = "",
    @SerialName("start_time") val startTime: Int = 0,
    @SerialName("end_time") val endTime: Int = 0,
    val definite: Boolean = false,
    val words: List<AsrWordPayload> = emptyList()
)

@Serializable
private data class AsrWordPayload(
    val text: String = "",
    @SerialName("start_time") val startTime: Int = 0,
    @SerialName("end_time") val endTime: Int = 0
)

@Serializable
private data class AudioInfoPayload(
    val duration: Int = 0
)

internal fun parseResponse(msg: ByteArray): AsrResponse {
    val buf = ByteBuffer.wrap(msg)
    val firstByte = buf.get().toInt() and 0xFF
    val headerSize = firstByte and 0x0F
    val secondByte = buf.get().toInt() and 0xFF
    val messageType = secondByte shr 4
    val flags = secondByte and 0x0F
    val thirdByte = buf.get().toInt() and 0xFF
    val compression = thirdByte and 0x0F
    buf.get() // reserved

    repeat((headerSize - 1) * 4) { buf.get() }

    var payloadSequence = 0
    var isLastPackage = false
    var code = 0

    if (flags and 0x01 != 0) {
        payloadSequence = buf.int
    }
    if (flags and 0x02 != 0) {
        isLastPackage = true
    }
    if (flags and 0x04 != 0) {
        buf.int // event, skip
    }

    when (messageType) {
        MessageType.SERVER_ERROR_RESPONSE -> {
            code = buf.int
            val errSize = buf.int
            val errBytes = ByteArray(errSize).also { buf.get(it) }
            val errMsg = String(errBytes, Charsets.UTF_8)
            android.util.Log.e("AsrSession", "server error $code: $errMsg")
            return AsrResponse(code, isLastPackage, payloadSequence, null, errMsg)
        }
        MessageType.SERVER_FULL_RESPONSE -> {
            buf.int // payload size
        }
    }

    if (!buf.hasRemaining()) return AsrResponse(code, isLastPackage, payloadSequence, null, null)

    val payload = ByteArray(buf.remaining()).also { buf.get(it) }
    val decompressed = if (compression == Compression.GZIP) gzipDecompress(payload) else payload

    return try {
        val payloadJson = responseJson.decodeFromString<AsrResponsePayload>(String(decompressed))
        val result = payloadJson.result?.let { resultPayload ->
            AsrResult(
                text = resultPayload.text,
                utterances = resultPayload.utterances.map { utterance ->
                    AsrUtterance(
                        text = utterance.text,
                        startTime = utterance.startTime,
                        endTime = utterance.endTime,
                        definite = utterance.definite,
                        words = utterance.words.map { word ->
                            AsrWord(word.text, word.startTime, word.endTime)
                        }
                    )
                },
                audioDuration = payloadJson.audioInfo?.duration ?: 0
            )
        }
        AsrResponse(code, isLastPackage, payloadSequence, result, payloadJson.error)
    } catch (e: Exception) {
        AsrResponse(code, isLastPackage, payloadSequence, null, e.message)
    }
}
