package cn.sanbei101.aivoiceime.asr

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission

private const val SAMPLE_RATE = 16000
private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
private const val TAG = "AudioRecorder"
private const val STOP_JOIN_TIMEOUT_MS = 200L

class AudioRecorder {
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    @Volatile private var recording = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onPcm: (ByteArray, Int) -> Unit): Result<Unit> {
        if (recording) {
            return Result.failure(IllegalStateException("AudioRecorder is already recording"))
        }

        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufSize <= 0) {
            return Result.failure(IllegalStateException("Invalid AudioRecord buffer size: $bufSize"))
        }

        val audioRecord = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        }.getOrElse { return Result.failure(it) }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return Result.failure(IllegalStateException("AudioRecord initialization failed"))
        }

        return runCatching {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord did not enter recording state")
            }
            recorder = audioRecord
            recording = true
            recordThread = Thread({
                val buf = ByteArray(bufSize)
                while (recording) {
                    val n = audioRecord.read(buf, 0, buf.size)
                    when {
                        n > 0 -> onPcm(buf, n)
                        n < 0 -> Log.w(TAG, "AudioRecord.read failed: $n")
                    }
                }
            }, TAG).also { it.start() }
        }.onFailure {
            recording = false
            recorder = null
            runCatching { audioRecord.release() }
        }
    }

    fun stop() {
        recording = false
        val audioRecord = recorder
        val thread = recordThread
        recorder = null
        recordThread = null
        audioRecord?.let {
            runCatching {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
            }.onFailure { error ->
                Log.w(TAG, "AudioRecord stop failed", error)
            }
        }
        if (thread != null && thread != Thread.currentThread()) {
            runCatching { thread.join(STOP_JOIN_TIMEOUT_MS) }.onFailure { error ->
                Log.w(TAG, "AudioRecord thread join failed", error)
            }
        }
        audioRecord?.let {
            runCatching { it.release() }.onFailure { error ->
                Log.w(TAG, "AudioRecord release failed", error)
            }
        }
    }
}
