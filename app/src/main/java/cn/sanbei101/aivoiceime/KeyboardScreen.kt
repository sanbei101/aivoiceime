package cn.sanbei101.aivoiceime

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.sanbei101.aivoiceime.pinyin.PinyinCandidate
import cn.sanbei101.aivoiceime.ui.theme.BgColor
import cn.sanbei101.aivoiceime.ui.theme.FunctionKeyColor
import cn.sanbei101.aivoiceime.ui.theme.KeyColor
import cn.sanbei101.aivoiceime.ui.theme.TextWhite


private val row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
private val row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
private val row3 = listOf("Z", "X", "C", "V", "B", "N", "M")

@Composable
fun KeyboardScreen(
    pinyinText: String,
    candidates: List<PinyinCandidate>,
    isRecording: Boolean,
    audioVolume: Float = 0f,
    onAlphabetClick: (String) -> Unit,
    onDelete: () -> Unit,
    onCommitText: (String) -> Unit,
    onEnter: () -> Unit,
    onHide: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit
) {
    val onSpaceClick = {
        val firstCandidate = candidates.firstOrNull()?.word
        when {
            firstCandidate != null -> onCommitText(firstCandidate)
            pinyinText.isNotEmpty() -> onCommitText(pinyinText)
            else -> onCommitText(" ")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        if (pinyinText.isEmpty()) {
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
                        .clickable { /* TODO */ }
                )
                Surface(
                    modifier = Modifier
                        .width(130.dp)
                        .height(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onRecordStart()
                                    tryAwaitRelease()
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
                        if (isRecording) {
                            AudioVisualizer(
                                volume = audioVolume,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "语音输入",
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
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
        } else {
            CandidateRow(
                pinyin = pinyinText,
                candidates = candidates,
                onCandidate = { onCommitText(it.word) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row1.forEach { char ->
                    KeyButton(char, Modifier.weight(1f), onClick = onAlphabetClick)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.weight(0.5f))
                row2.forEach { char ->
                    KeyButton(char, Modifier.weight(1f), onClick = onAlphabetClick)
                }
                Spacer(modifier = Modifier.weight(0.5f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyIconButton(
                    imageVector = Icons.Default.KeyboardCapslock,
                    contentDescription = "大小写切换",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = FunctionKeyColor,
                    onClick = { /* TODO: 处理大小写切换逻辑 */ }
                )

                row3.forEach { char ->
                    KeyButton(char, Modifier.weight(1f), onClick = onAlphabetClick)
                }

                KeyIconButton(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "删除",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = FunctionKeyColor,
                    iconSize = 20.dp,
                    onClick = onDelete
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("123", Modifier.weight(1.5f), FunctionKeyColor, onClick = { /* TODO */ })
                KeyButton(
                    ",",
                    Modifier.weight(1f),
                    FunctionKeyColor,
                    onClick = { onCommitText(",") })

                KeyIconButton(
                    imageVector = Icons.Default.SpaceBar,
                    contentDescription = "空格",
                    modifier = Modifier.weight(4.3f),
                    backgroundColor = KeyColor,
                    iconSize = 18.dp,
                    onClick = onSpaceClick
                )
                KeyIconButton(
                    imageVector = Icons.Default.Language,
                    contentDescription = "切换语言",
                    modifier = Modifier.weight(1.4f),
                    backgroundColor = FunctionKeyColor,
                    iconSize = 20.dp,
                    onClick = { /* TODO: 处理中英文切换逻辑 */ }
                )

                KeyButton(
                    "换行",
                    Modifier.weight(1.8f),
                    FunctionKeyColor,
                    onClick = { onEnter() }
                )
            }
        }
    }
}

@Composable
fun CandidateRow(
    pinyin: String,
    candidates: List<PinyinCandidate>,
    onCandidate: (PinyinCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.height(32.dp),
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

        Spacer(modifier = Modifier.width(8.dp))

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(candidates, key = { it.word }) { candidate ->
                Surface(
                    onClick = { onCandidate(candidate) },
                    modifier = Modifier.height(34.dp),
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
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
    onClick: (String) -> Unit
) {
    Surface(
        onClick = { onClick(text) },
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

@Composable
fun AudioVisualizer(
    volume: Float,
    modifier: Modifier = Modifier
) {
    val animatedVolume by animateFloatAsState(
        targetValue = volume.coerceIn(0.15f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "volume_anim"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (0..3).forEach { index ->
            val heightMultiplier = remember(index) {
                when (index) {
                    0, 3 -> 0.7f
                    else -> 1.0f
                }
            }
            val barHeight = (animatedVolume * heightMultiplier).coerceIn(0.15f, 1f)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(barHeight)
                    .background(TextWhite, RoundedCornerShape(1.5.dp))
            )
        }
    }
}