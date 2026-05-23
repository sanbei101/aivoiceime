package cn.sanbei101.aivoiceime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.sanbei101.aivoiceime.pinyin.PinyinCandidate
import cn.sanbei101.aivoiceime.pinyin.PinyinDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeyboardViewModel(private val pinyinDao: PinyinDao) : ViewModel() {
    var pinyinText by mutableStateOf("")
        private set
    var candidates by mutableStateOf<List<PinyinCandidate>>(emptyList())
        private set
    var isRecording by mutableStateOf(false)
        private set

    private val pinyinBuffer = StringBuilder()
    private var searchJob: Job? = null
    fun appendPinyin(char: String) {
        pinyinBuffer.append(char.lowercase())
        updatePinyinState()
    }

    fun deleteLast() {
        if (pinyinBuffer.isNotEmpty()) {
            pinyinBuffer.deleteAt(pinyinBuffer.lastIndex)
            updatePinyinState()
        }
    }

    fun clear() {
        pinyinBuffer.clear()
        updatePinyinState()
    }

    fun setRecordingState(recording: Boolean) {
        isRecording = recording
    }

    private fun updatePinyinState() {
        pinyinText = pinyinBuffer.toString()
        searchJob?.cancel()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            if (pinyinText.isBlank()) {
                candidates = emptyList()
                return@launch
            }

            delay(100)

            val result = pinyinDao.candidates(pinyinText)
            candidates = result
        }
    }
}