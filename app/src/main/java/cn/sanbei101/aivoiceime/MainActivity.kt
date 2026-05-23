package cn.sanbei101.aivoiceime

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.sanbei101.aivoiceime.ui.theme.AivoiceimeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainActivity", "RECORD_AUDIO granted=$granted")
        if (!granted) {
            Toast.makeText(this@MainActivity, "需要麦克风权限才能使用语音输入", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        enableEdgeToEdge()
        setContent {
            AivoiceimeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImeGuideScreen(
                        modifier = Modifier.padding(innerPadding),
                        onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImeGuideScreen(modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    var netStatus by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("欢迎使用 AI 语音输入法", fontSize = 20.sp, modifier = Modifier.padding(bottom = 24.dp))

        Button(onClick = onOpenSettings) {
            Text("第一步：去系统设置中激活输入法")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            netStatus = "测试中..."
            CoroutineScope(Dispatchers.IO).launch {
                val status = try {
                    val resp = OkHttpClient().newCall(
                        Request.Builder().url("https://baidu.com").head().build()
                    ).execute()
                    "HTTP ${resp.code}"
                } catch (e: Exception) {
                    "失败: ${e.message}"
                }
                netStatus = status
            }
        }) {
            Text("测试网络连通性")
        }

        if (netStatus.isNotEmpty()) {
            Text(netStatus, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Text(
            "提示：激活后，打开任意输入框，切换输入法即可使用",
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
