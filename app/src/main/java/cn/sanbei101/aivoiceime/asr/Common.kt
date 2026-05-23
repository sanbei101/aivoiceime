package cn.sanbei101.aivoiceime.asr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal const val PROTOCOL_VERSION: Int = 0x01

internal object MessageType {
    const val CLIENT_FULL_REQUEST: Int = 0b0001
    const val CLIENT_AUDIO_ONLY_REQUEST: Int = 0b0010
    const val SERVER_FULL_RESPONSE: Int = 0b1001
    const val SERVER_ERROR_RESPONSE: Int = 0b1111
}

internal object Flags {
    const val NO_SEQUENCE: Int = 0b0000
    const val POS_SEQUENCE: Int = 0b0001
    const val NEG_SEQUENCE: Int = 0b0010
    const val NEG_WITH_SEQUENCE: Int = 0b0011
}

internal object Serialization {
    const val NO_SERIALIZATION: Int = 0b0000
    const val JSON: Int = 0b0001
}

internal object Compression {
    const val GZIP: Int = 0b0001
}

internal fun gzipCompress(input: ByteArray): ByteArray {
    return gzipCompress(input, 0, input.size)
}

internal fun gzipCompress(input: ByteArray, offset: Int, length: Int): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(input, offset, length) }
    return out.toByteArray()
}

internal fun gzipDecompress(input: ByteArray): ByteArray {
    return GZIPInputStream(ByteArrayInputStream(input)).use { it.readBytes() }
}

internal fun isWav(data: ByteArray): Boolean {
    if (data.size < 12) return false
    return String(data, 0, 4) == "RIFF" && String(data, 8, 4) == "WAVE"
}

internal data class WavInfo(
    val channels: Int,
    val sampleWidth: Int,
    val frameRate: Int,
    val audioData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WavInfo

        if (channels != other.channels) return false
        if (sampleWidth != other.sampleWidth) return false
        if (frameRate != other.frameRate) return false
        if (!audioData.contentEquals(other.audioData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = channels
        result = 31 * result + sampleWidth
        result = 31 * result + frameRate
        result = 31 * result + audioData.contentHashCode()
        return result
    }
}

internal fun readWavInfo(data: ByteArray): WavInfo {
    val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    buf.position(22)
    val channels = buf.short.toInt() and 0xFFFF
    val frameRate = buf.int
    buf.position(34)
    val bitsPerSample = buf.short.toInt() and 0xFFFF
    val sampleWidth = bitsPerSample / 8
    buf.position(44)
    val audioData = ByteArray(buf.remaining())
    buf.get(audioData)
    return WavInfo(channels, sampleWidth, frameRate, audioData)
}
