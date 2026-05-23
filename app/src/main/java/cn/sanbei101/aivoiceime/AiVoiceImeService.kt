package cn.sanbei101.aivoiceime

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
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
import cn.sanbei101.aivoiceime.pinyin.PinyinDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"

class AiVoiceImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private lateinit var voiceManager: VoiceInputManager
    private lateinit var viewModel: KeyboardViewModel

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()

        val pinyinDao = PinyinDatabase.getInstance(this).pinyinDao()
        viewModel = KeyboardViewModel(pinyinDao)
        voiceManager = VoiceInputManager(BuildConfig.ASR_API_KEY)
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        viewModel.clear()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        stopVoiceInput()
    }

    override fun onDestroy() {
        stopVoiceInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        viewModel.setRecordingState(true)
        voiceManager.start(lifecycleScope) { text, isLast ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (isLast) {
                    currentInputConnection?.commitText(text, 1)
                } else {
                    currentInputConnection?.setComposingText(text, 1)
                }
            }
        }
    }

    private fun stopVoiceInput() {
        viewModel.setRecordingState(false)
        voiceManager.stop()
    }
    override fun onCreateInputView(): View {
        window.window?.decorView?.apply {
            setViewTreeLifecycleOwner(this@AiVoiceImeService)
            setViewTreeViewModelStoreOwner(this@AiVoiceImeService)
            setViewTreeSavedStateRegistryOwner(this@AiVoiceImeService)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AiVoiceImeService)
            setViewTreeViewModelStoreOwner(this@AiVoiceImeService)
            setViewTreeSavedStateRegistryOwner(this@AiVoiceImeService)

            setContent {
                KeyboardScreen(
                    pinyinText = viewModel.pinyinText,
                    candidates = viewModel.candidates,
                    isRecording = viewModel.isRecording,
                    onAlphabetClick = { viewModel.appendPinyin(it) },
                    onDelete = {
                        if (viewModel.pinyinText.isNotEmpty()) {
                            viewModel.deleteLast()
                        } else {
                            currentInputConnection?.sendKeyEvent(
                                KeyEvent(
                                    KeyEvent.ACTION_DOWN,
                                    KeyEvent.KEYCODE_DEL
                                )
                            )
                            currentInputConnection?.sendKeyEvent(
                                KeyEvent(
                                    KeyEvent.ACTION_UP,
                                    KeyEvent.KEYCODE_DEL
                                )
                            )
                        }
                    },
                    onCommitText = { text ->
                        currentInputConnection?.commitText(text, 1)
                        viewModel.clear()
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