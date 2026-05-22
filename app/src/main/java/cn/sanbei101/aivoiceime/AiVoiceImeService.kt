package cn.sanbei101.aivoiceime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class AiVoiceImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
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
                    onDelete = { currentInputConnection?.deleteSurroundingText(1, 0) },
                    onEnter = {
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    },
                    onHide = { requestHideSelf(0) }
                )
            }
        }
    }
}


val BgColor = Color(0xFF1E1E1E)
val KeyColor = Color(0xFF424242)
val FunctionKeyColor = Color(0xFF303030)
val TextWhite = Color(0xFFFFFFFF)

@Composable
fun KeyboardScreen(
    onText: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onHide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .padding(bottom = 8.dp)
    ) {
        // 顶部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⚙️", color = TextWhite, fontSize = 20.sp, modifier = Modifier.clickable { })

            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FunctionKeyColor)
                    .clickable { onText("[豆包AI识别的文本]") },
                contentAlignment = Alignment.Center
            ) {
                Text("🎙️ 点击说话", color = TextWhite, fontSize = 13.sp)
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
                listOf("Q","W","E","R","T","Y","U","I","O","P").forEach { char ->
                    KeyButton(char, Modifier.weight(1f)) { onText(char) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.weight(0.5f))
                listOf("A","S","D","F","G","H","J","K","L").forEach { char ->
                    KeyButton(char, Modifier.weight(1f)) { onText(char) }
                }
                Spacer(modifier = Modifier.weight(0.5f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyButton("⇧", Modifier.weight(1.5f), FunctionKeyColor) { }
                listOf("Z","X","C","V","B","N","M").forEach { char ->
                    KeyButton(char, Modifier.weight(1f)) { onText(char) }
                }
                KeyButton("⌫", Modifier.weight(1.5f), FunctionKeyColor) { onDelete() }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("123", Modifier.weight(1.5f), FunctionKeyColor) {}
                KeyButton(",", Modifier.weight(1f), FunctionKeyColor) { onText(",") }

                Box(
                    modifier = Modifier
                        .weight(4.5f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KeyColor)
                        .clickable { onText(" ") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⌴    |i|i|", color = Color.LightGray, fontSize = 14.sp)
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
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}