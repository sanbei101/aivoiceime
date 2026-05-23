package cn.sanbei101.aivoiceime.asr

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

private const val SAMPLE_RATE = 16000
private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

class AudioRecorder {
    private var recorder: AudioRecord? = null
    @Volatile private var recording = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onPcm: (ByteArray) -> Unit) {
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        recording = true
        recorder!!.startRecording()
        Thread {
            val buf = ByteArray(bufSize)
            while (recording) {
                val n = recorder?.read(buf, 0, buf.size) ?: break
                if (n > 0) onPcm(buf.copyOf(n))
            }
        }.start()
    }

    fun stop() {
        recording = false
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}
