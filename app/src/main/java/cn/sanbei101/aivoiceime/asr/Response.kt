package cn.sanbei101.aivoiceime.asr

import org.json.JSONObject
import java.nio.ByteBuffer

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
        val json = JSONObject(String(decompressed))
        val errorMsg = json.optString("error").takeIf { it.isNotEmpty() }
        val resultJson = json.optJSONObject("result")
        val audioInfo = json.optJSONObject("audio_info")

        val result = resultJson?.let {
            val utterancesJson = it.optJSONArray("utterances")
            val utterances = (0 until (utterancesJson?.length() ?: 0)).map { i ->
                val u = utterancesJson!!.getJSONObject(i)
                val wordsJson = u.optJSONArray("words")
                val words = (0 until (wordsJson?.length() ?: 0)).map { j ->
                    val w = wordsJson!!.getJSONObject(j)
                    AsrWord(w.optString("text"), w.optInt("start_time"), w.optInt("end_time"))
                }
                AsrUtterance(u.optString("text"), u.optInt("start_time"), u.optInt("end_time"), u.optBoolean("definite"), words)
            }
            AsrResult(it.optString("text"), utterances, audioInfo?.optInt("duration") ?: 0)
        }
        AsrResponse(code, isLastPackage, payloadSequence, result, errorMsg)
    } catch (e: Exception) {
        AsrResponse(code, isLastPackage, payloadSequence, null, e.message)
    }
}
