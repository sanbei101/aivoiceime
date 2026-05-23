package cn.sanbei101.aivoiceime

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import cn.sanbei101.aivoiceime.pinyin.PinyinCandidate
import cn.sanbei101.aivoiceime.pinyin.PinyinDao
import cn.sanbei101.aivoiceime.pinyin.PinyinDatabase
import cn.sanbei101.aivoiceime.ui.theme.BgColor
import cn.sanbei101.aivoiceime.ui.theme.FunctionKeyColor
import cn.sanbei101.aivoiceime.ui.theme.KeyColor
import cn.sanbei101.aivoiceime.ui.theme.TextWhite
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
    private val pinyinDao: PinyinDao by lazy { PinyinDatabase.getInstance(this).pinyinDao() }

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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val s = asrClient.startSession()
        session = s
        lifecycleScope.launch {
            s.responses.collect { resp ->
                val text = resp.result?.text ?: return@collect
                withContext(Dispatchers.Main) {
                    if (resp.isLastPackage) {
                        currentInputConnection?.commitText(text, 1)
                    } else {
                        currentInputConnection?.setComposingText(text, 1)
                    }
                }
            }
        }
        recorder.start { pcm, length -> s.sendPcm(pcm, 0, length) }
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
                    pinyinDao = pinyinDao,
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


private val row1 = listOf("Q","W","E","R","T","Y","U","I","O","P")
private val row2 = listOf("A","S","D","F","G","H","J","K","L")
private val row3 = listOf("Z","X","C","V","B","N","M")

@Composable
fun KeyboardScreen(
    pinyinDao: PinyinDao,
    onText: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onHide: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var pinyinBuffer by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<PinyinCandidate>()) }

    fun clearPinyin() {
        pinyinBuffer = ""
        candidates = emptyList()
    }

    fun commitCandidate(text: String) {
        onText(text)
        clearPinyin()
    }

    fun commitFirstCandidateOrSpace() {
        val firstCandidate = candidates.firstOrNull()?.word
        when {
            firstCandidate != null -> commitCandidate(firstCandidate)
            pinyinBuffer.isNotEmpty() -> commitCandidate(pinyinBuffer)
            else -> onText(" ")
        }
    }

    LaunchedEffect(pinyinBuffer) {
        candidates = if (pinyinBuffer.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                pinyinDao.candidates(pinyinBuffer.lowercase())
            }
        }
    }

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
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = TextWhite,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { /* TODO: 点击设置 */ }
            )
            Surface(
                modifier = Modifier
                    .width(130.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "语音输入",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "识别中..." else "按住说话",
                        color = TextWhite,
                        fontSize = 13.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardHide,
                contentDescription = "隐藏键盘",
                tint = TextWhite,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onHide() }
            )
        }

        CandidateRow(
            pinyin = pinyinBuffer,
            candidates = candidates,
            onCandidate = { commitCandidate(it.word) }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row1.forEach { char ->
                    KeyButton(char, Modifier.weight(1f)) {
                        pinyinBuffer += char.lowercase()
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.weight(0.5f))
                row2.forEach { char ->
                    KeyButton(char, Modifier.weight(1f)) {
                        pinyinBuffer += char.lowercase()
                    }
                }
                Spacer(modifier = Modifier.weight(0.5f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyIconButton(
                    imageVector = Icons.Default.KeyboardCapslock,
                    contentDescription = "大小写切换",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = FunctionKeyColor
                ) { /* TODO: 处理大小写切换逻辑 */ }

                row3.forEach { char ->
                    KeyButton(char, Modifier.weight(1f)) {
                        pinyinBuffer += char.lowercase()
                    }
                }

                KeyIconButton(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "删除",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = FunctionKeyColor,
                    iconSize = 20.dp
                ) {
                    if (pinyinBuffer.isNotEmpty()) {
                        pinyinBuffer = pinyinBuffer.dropLast(1)
                    } else {
                        onDelete()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("123", Modifier.weight(1.5f), FunctionKeyColor) {}
                KeyButton(",", Modifier.weight(1f), FunctionKeyColor) { onText(",") }

                KeyIconButton(
                    imageVector = Icons.Default.SpaceBar,
                    contentDescription = "空格",
                    modifier = Modifier.weight(4.3f),
                    backgroundColor = KeyColor,
                    iconSize = 18.dp
                ) { commitFirstCandidateOrSpace() }
                KeyIconButton(
                    imageVector = Icons.Default.Language,
                    contentDescription = "切换语言",
                    modifier = Modifier.weight(1.4f),
                    backgroundColor = FunctionKeyColor,
                    iconSize = 20.dp
                ) { /* TODO: 处理中英文切换逻辑 */ }

                KeyButton("换行", Modifier.weight(1.8f), FunctionKeyColor) { onEnter() }
            }
        }
    }
}

@Composable
fun CandidateRow(
    pinyin: String,
    candidates: List<PinyinCandidate>,
    onCandidate: (PinyinCandidate) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pinyin.isNotEmpty()) {
            Surface(
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(6.dp),
                color = FunctionKeyColor
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pinyin,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(candidates) { candidate ->
                    Surface(
                        onClick = { onCandidate(candidate) },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = KeyColor
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = candidate.word,
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
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

@Composable
fun KeyIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = KeyColor,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = TextWhite,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
