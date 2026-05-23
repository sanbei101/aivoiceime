package cn.sanbei101.aivoiceime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cn.sanbei101.aivoiceime.asr.AsrSession
import cn.sanbei101.aivoiceime.asr.AsrWsClient
import cn.sanbei101.aivoiceime.asr.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ACCESS_KEY = "ec2cd821-0358-497c-80a6-cecd5b22e1ea"
private const val ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"

class AiVoiceImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private val recorder = AudioRecorder()
    private val asrClient = AsrWsClient(ASR_URL, ACCESS_KEY)
    private var session: AsrSession? = null

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun startVoiceInput() {
        val s = asrClient.startSession()
        session = s
        lifecycleScope.launch {
            s.responses.collect { resp ->
                val text = resp.result?.text ?: return@collect
                withContext(Dispatchers.Main) {
                    if (resp.isLastPackage) {
                        currentInputConnection?.finishComposingText()
                        currentInputConnection?.commitText(text, 1)
                    } else {
                        currentInputConnection?.setComposingText(text, 1)
                    }
                }
            }
        }
        recorder.start { pcm -> s.sendPcm(pcm) }
    }

    fun stopVoiceInput() {
        recorder.stop()
        session?.finish()
        session = null
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            window?.window?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(this@AiVoiceImeService)
                decorView.setViewTreeViewModelStoreOwner(this@AiVoiceImeService)
                decorView.setViewTreeSavedStateRegistryOwner(this@AiVoiceImeService)
            }
            setViewTreeLifecycleOwner(this@AiVoiceImeService)
            setViewTreeViewModelStoreOwner(this@AiVoiceImeService)
            setViewTreeSavedStateRegistryOwner(this@AiVoiceImeService)

            setContent {
                KeyboardScreen(
                    onText = { currentInputConnection?.commitText(it, 1) },
                    onDelete = {
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                    },
                    onEnter = {
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    },
                    onHide = { requestHideSelf(0) },
                    onRecordStart = { startVoiceInput() },
                    onRecordStop = { stopVoiceInput() }
                )
            }
        }
    }
}

val BgColor = Color(0xFF1E1E1E)
val KeyColor = Color(0xFF424242)
val FunctionKeyColor = Color(0xFF303030)
val TextWhite = Color(0xFFFFFFFF)

private val row1 = listOf("Q","W","E","R","T","Y","U","I","O","P")
private val row2 = listOf("A","S","D","F","G","H","J","K","L")
private val row3 = listOf("Z","X","C","V","B","N","M")

@Composable
fun KeyboardScreen(
    onText: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onHide: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⚙️", color = TextWhite, fontSize = 20.sp, modifier = Modifier.clickable { })

            Surface(
                modifier = Modifier
                    .width(120.dp)
                    .height(32.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isRecording = true
                                onRecordStart()
                                tryAwaitRelease()
                                isRecording = false
                                onRecordStop()
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isRecording) Color(0xFFD32F2F) else FunctionKeyColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isRecording) "🔴 识别中..." else "🎙️ 按住说话",
                        color = TextWhite,
                        fontSize = 13.sp
                    )
                }
            }

            Text("🔽", color = TextWhite, fontSize = 18.sp, modifier = Modifier.clickable { onHide() })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row1.forEach { char -> KeyButton(char, Modifier.weight(1f)) { onText(char) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.weight(0.5f))
                row2.forEach { char -> KeyButton(char, Modifier.weight(1f)) { onText(char) } }
                Spacer(modifier = Modifier.weight(0.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyButton("⇧", Modifier.weight(1.5f), FunctionKeyColor) { }
                row3.forEach { char -> KeyButton(char, Modifier.weight(1f)) { onText(char) } }
                KeyButton("⌫", Modifier.weight(1.5f), FunctionKeyColor) { onDelete() }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("123", Modifier.weight(1.5f), FunctionKeyColor) {}
                KeyButton(",", Modifier.weight(1f), FunctionKeyColor) { onText(",") }
                Surface(
                    onClick = { onText(" ") },
                    modifier = Modifier.weight(4.5f).height(46.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = KeyColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⌴    |i|i|", color = Color.LightGray, fontSize = 14.sp)
                    }
                }
                KeyButton("中/英", Modifier.weight(1.2f), FunctionKeyColor) {}
                KeyButton("换行", Modifier.weight(1.8f), FunctionKeyColor) { onEnter() }
            }
        }
    }
}

@Composable
fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = KeyColor,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
