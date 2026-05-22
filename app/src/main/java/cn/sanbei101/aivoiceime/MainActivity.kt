package cn.sanbei101.aivoiceime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.sanbei101.aivoiceime.ui.theme.AivoiceimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AivoiceimeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImeGuideScreen(
                        modifier = Modifier.padding(innerPadding),
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImeGuideScreen(modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "欢迎使用 AI 语音输入法", fontSize = 20.sp, modifier = Modifier.padding(bottom = 24.dp))

        Button(onClick = onOpenSettings) {
            Text(text = "第一步：去系统设置中激活输入法")
        }
        Text(
            text = "提示:激活后,打开任意输入框,切换输入法即可使用",
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}