package com.qiandaizi.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiandaizi.app.BuildConfig
import com.qiandaizi.app.core.ApkDownloader
import com.qiandaizi.app.core.AppGraph
import com.qiandaizi.app.core.TextMain
import com.qiandaizi.app.core.TextSub
import com.qiandaizi.app.core.UpdateChecker

/**
 * 检查更新的全局状态 + 弹窗宿主（参考 Solarpanel-app）。
 * MainShell 挂载 [UpdateDialogs] 并做一次静默检查；
 * 「关于我们」页手动触发 [UpdateFlow.checkFromUser]。
 */
object UpdateFlow {
    sealed class Ui {
        object Idle : Ui()
        data class Found(val info: UpdateChecker.ReleaseInfo) : Ui()
        data class Downloading(val info: UpdateChecker.ReleaseInfo, val percent: Int) : Ui()
        data class Failed(val message: String) : Ui()
    }

    var ui by mutableStateOf<Ui>(Ui.Idle)
        private set
    private var silentDone = false
    private var checking = false

    /** 冷启动静默检查：仅在发现新版本时弹窗，失败/已最新都不打扰。 */
    fun silentCheck() {
        if (silentDone) return
        silentDone = true
        runCheck(silent = true)
    }

    /** 「关于我们」页手动触发：已最新 / 失败都给反馈。 */
    fun checkFromUser() {
        AppGraph.state.notify("正在检查更新…")
        runCheck(silent = false)
    }

    private fun runCheck(silent: Boolean) {
        if (checking) return
        checking = true
        UpdateChecker.check(BuildConfig.VERSION_NAME) { result ->
            checking = false
            when (result) {
                is UpdateChecker.Result.HasUpdate -> ui = Ui.Found(result.info)
                UpdateChecker.Result.UpToDate -> {
                    if (!silent) AppGraph.state.notify("已是最新版本")
                }
                is UpdateChecker.Result.Error -> {
                    if (!silent) ui = Ui.Failed(result.message)
                }
            }
        }
    }

    fun dismiss() {
        ui = Ui.Idle
    }

    fun download(context: android.content.Context, info: UpdateChecker.ReleaseInfo) {
        ui = Ui.Downloading(info, 0)
        ApkDownloader.download(context, info.apkUrl) { state ->
            when (state) {
                is ApkDownloader.State.Progress ->
                    ui = Ui.Downloading(info, state.percent)
                is ApkDownloader.State.Done -> {
                    ui = Ui.Idle
                    runCatching { ApkDownloader.install(context, state.file) }
                        .onFailure { AppGraph.state.notify("无法打开安装器") }
                }
                is ApkDownloader.State.Error ->
                    ui = Ui.Failed("下载失败：${state.message}")
            }
        }
    }
}

/** 渲染更新相关弹窗：发现新版本 / 下载进度 / 失败原因。挂在 MainShell 全局生效。 */
@Composable
fun UpdateDialogs() {
    val context = LocalContext.current
    when (val s = UpdateFlow.ui) {
        is UpdateFlow.Ui.Idle -> Unit

        is UpdateFlow.Ui.Found -> AlertDialog(
            onDismissRequest = { UpdateFlow.dismiss() },
            title = { Text("发现新版本 v${s.info.versionName}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (s.info.notes.isNotBlank()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFFF6F7F9))
                                .padding(12.dp)
                        ) {
                            Text(s.info.notes, fontSize = 13.sp, color = TextSub,
                                lineHeight = 20.sp)
                        }
                    } else {
                        Text("当前版本 v${BuildConfig.VERSION_NAME}",
                            fontSize = 13.sp, color = TextSub)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateFlow.download(context, s.info) }) {
                    Text("下载更新", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { UpdateFlow.dismiss() }) { Text("暂不") }
            }
        )

        is UpdateFlow.Ui.Downloading -> AlertDialog(
            onDismissRequest = { /* 下载中不可取消 */ },
            title = { Text("正在下载 v${s.info.versionName}") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { if (s.percent >= 0) s.percent / 100f else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (s.percent >= 0) "${s.percent}%" else "下载中…",
                        fontSize = 12.sp, color = TextSub
                    )
                }
            },
            confirmButton = {}
        )

        is UpdateFlow.Ui.Failed -> AlertDialog(
            onDismissRequest = { UpdateFlow.dismiss() },
            title = { Text("检查更新失败") },
            text = { Text(s.message, fontSize = 13.sp, color = TextMain) },
            confirmButton = {
                TextButton(onClick = {
                    UpdateFlow.dismiss()
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/qiancheng817/qiandaizi-app/releases/latest")
                        )
                    )
                }) { Text("浏览器打开") }
            },
            dismissButton = {
                TextButton(onClick = { UpdateFlow.dismiss() }) { Text("关闭") }
            }
        )
    }
}
