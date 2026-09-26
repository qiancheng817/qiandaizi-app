package com.qiandaizi.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiandaizi.app.BuildConfig
import com.qiandaizi.app.core.AppGraph
import com.qiandaizi.app.core.MetaDto
import com.qiandaizi.app.core.TextMain
import com.qiandaizi.app.core.TextSub
import com.qiandaizi.app.core.UpdateMeReq
import com.qiandaizi.app.core.explainError
import com.qiandaizi.app.ui.common.PrimaryButton
import com.qiandaizi.app.ui.common.QianField
import com.qiandaizi.app.ui.common.SubPageScaffold
import com.qiandaizi.app.ui.common.UpdateFlow
import com.qiandaizi.app.ui.common.WhiteCard
import com.qiandaizi.app.ui.common.AppIcon
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val appState = AppGraph.state
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf(appState.user()?.nickname ?: "") }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    SubPageScaffold(title = "账号管理", onBack = onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            WhiteCard {
                Text("基本资料", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = TextMain)
                Spacer(Modifier.height(12.dp))
                Text("登录账号：${appState.user()?.username}", fontSize = 13.sp,
                    color = TextSub)
                Spacer(Modifier.height(14.dp))
                QianField(
                    label = "昵称",
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = "给自己起个昵称"
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "保存昵称",
                    loading = saving,
                    onClick = {
                        errorMsg = null
                        saving = true
                        scope.launch {
                            runCatching {
                                appState.api().updateMe(
                                    UpdateMeReq(nickname = nickname.trim())
                                )
                            }.onSuccess {
                                appState.refreshAccountUser(it.user)
                                appState.bump()
                                appState.notify("昵称已保存")
                            }.onFailure { errorMsg = explainError(it) }
                            saving = false
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            WhiteCard {
                Text("修改密码", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = TextMain)
                Spacer(Modifier.height(12.dp))
                QianField(
                    label = "当前密码",
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    password = true,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                )
                Spacer(Modifier.height(12.dp))
                QianField(
                    label = "新密码",
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    password = true,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                )
                Spacer(Modifier.height(12.dp))
                QianField(
                    label = "确认新密码",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    password = true,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                )
                if (errorMsg != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(errorMsg!!, fontSize = 13.sp,
                        color = com.qiandaizi.app.core.ExpenseRed)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "修改密码",
                    loading = saving,
                    onClick = {
                        errorMsg = null
                        if (oldPassword.isBlank() || newPassword.isBlank()) {
                            errorMsg = "请填写当前密码与新密码"
                            return@PrimaryButton
                        }
                        if (newPassword.length < 6) {
                            errorMsg = "新密码至少 6 位"
                            return@PrimaryButton
                        }
                        if (newPassword != confirmPassword) {
                            errorMsg = "两次输入的新密码不一致"
                            return@PrimaryButton
                        }
                        saving = true
                        scope.launch {
                            runCatching {
                                appState.api().updateMe(
                                    UpdateMeReq(
                                        oldPassword = oldPassword,
                                        newPassword = newPassword
                                    )
                                )
                            }.onSuccess {
                                oldPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                appState.notify("密码已修改")
                            }.onFailure { errorMsg = explainError(it) }
                            saving = false
                        }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val appState = AppGraph.state
    val scope = rememberCoroutineScope()

    var meta by remember { mutableStateOf<MetaDto?>(null) }
    var loaded by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { appState.api().meta() }
            .onSuccess {
                meta = it
                loaded = true
            }
            .onFailure {
                loaded = true
            }
    }

    SubPageScaffold(title = "关于我们", onBack = onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            WhiteCard {
                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("钱袋子", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = TextMain)
                    Spacer(Modifier.height(4.dp))
                    Text("版本 v${BuildConfig.VERSION_NAME}", fontSize = 12.sp,
                        color = TextSub)
                    meta?.version?.let {
                        Text("服务端 v$it", fontSize = 11.sp, color = TextSub,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            WhiteCard {
                Row(Modifier.fillMaxWidth().clickable { UpdateFlow.checkFromUser() },
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("检查更新", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = TextMain)
                        Spacer(Modifier.height(2.dp))
                        Text("当前 v${BuildConfig.VERSION_NAME}，从 GitHub Releases 获取新版",
                            fontSize = 11.sp, color = TextSub)
                    }
                    Text("›", fontSize = 22.sp, color = TextSub)
                }
            }
            Spacer(Modifier.height(14.dp))
            WhiteCard {
                Text("服务器地址", fontSize = 12.sp, color = TextSub)
                Text(appState.server ?: "-", fontSize = 13.sp, color = TextMain,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Text("当前账号", fontSize = 12.sp, color = TextSub)
                Text(
                    "${appState.user()?.nickname ?: ""}（${appState.user()?.username ?: ""}）",
                    fontSize = 13.sp, color = TextMain,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "数据实时与后端服务器同步 · 原生 Android 客户端",
                fontSize = 11.sp, color = TextSub,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
