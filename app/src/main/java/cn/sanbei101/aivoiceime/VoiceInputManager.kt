package cn.sanbei101.aivoiceime

import android.Manifest
import androidx.annotation.RequiresPermission
import cn.sanbei101.aivoiceime.asr.AsrSession
import cn.sanbei101.aivoiceime.asr.AsrWsClient
import cn.sanbei101.aivoiceime.asr.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class VoiceInputManager(private val apiKey: String) {
    private val asrClient = AsrWsClient(ASR_URL, apiKey)
    private val recorder = AudioRecorder()
    private var session: AsrSession? = null
    private var responseJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        scope: CoroutineScope,
        onResult: (text: String, isLast: Boolean) -> Unit,
        onVolume: (Float) -> Unit
    ) {
        if (session != null) stop()

        session = asrClient.startSession().also { s ->
            responseJob = scope.launch {
                s.responses.collect { resp ->
                    resp.result?.text?.let { text ->
                        onResult(text, resp.isLastPackage)
                    }
                }
            }
        }

        recorder.start { pcm, length ->
            session?.sendPcm(pcm, 0, length)
            val volume = calculateVolume(pcm, length)
            onVolume(volume)
        }.onFailure { stop() }
    }

    fun stop() {
        recorder.stop()
        session?.finish()
        session = null
        responseJob?.cancel()
        responseJob = null
    }

    private fun calculateVolume(pcm: ByteArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length - 1 step 2) {
            val sample = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / (length / 2))
        return (rms / 10000.0).toFloat().coerceIn(0f, 1f)
    }
}