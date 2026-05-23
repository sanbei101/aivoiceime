package cn.sanbei101.aivoiceime

import android.Manifest
import androidx.annotation.RequiresPermission
import cn.sanbei101.aivoiceime.asr.AsrSession
import cn.sanbei101.aivoiceime.asr.AsrWsClient
import cn.sanbei101.aivoiceime.asr.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VoiceInputManager(private val apiKey: String) {
    private val asrClient = AsrWsClient(ASR_URL, apiKey)
    private val recorder = AudioRecorder()
    private var session: AsrSession? = null
    private var responseJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope, onResult: (text: String, isLast: Boolean) -> Unit) {
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

        recorder.start { pcm, length -> session?.sendPcm(pcm, 0, length) }
            .onFailure { stop() }
    }

    fun stop() {
        recorder.stop()
        session?.finish()
        session = null
        responseJob?.cancel()
        responseJob = null
    }
}