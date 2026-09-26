package com.qiandaizi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.qiandaizi.app.core.AppGraph
import com.qiandaizi.app.core.QianTheme
import com.qiandaizi.app.core.YellowDark
import com.qiandaizi.app.ui.RootGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 系统闪屏保持到会话加载完成，无缝交棒给 Compose 开屏页（SplashGate）
        splashScreen.setKeepOnScreenCondition { !AppGraph.state.loaded }
        setContent {
            QianTheme {
                var crash by remember { mutableStateOf<String?>(null) }
                var checked by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    crash = CrashStore.read(this@MainActivity)
                    checked = true
                }

                if (!checked) {
                    Box(Modifier.fillMaxSize())
                } else if (crash != null) {
                    CrashTraceDialog(
                        trace = crash!!,
                        appContext = applicationContext,
                        onContinue = {
                            CrashStore.clear(applicationContext)
                            crash = null
                        }
                    )
                } else {
                    RootGate()
                }
            }
        }
    }
}

/** 上次崩溃的堆栈展示，可复制后反馈 */
@Composable
private fun CrashTraceDialog(
    trace: String,
    appContext: android.content.Context,
    onContinue: () -> Unit
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = {},
        title = { Text("检测到程序异常退出", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(trace, fontSize = 10.sp, color = Color(0xFF555555))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(trace))
            }) { Text("复制日志", color = YellowDark) }
        },
        dismissButton = {
            TextButton(onClick = onContinue) { Text("清除并进入", color = Color(0xFF888888)) }
        }
    )
}
