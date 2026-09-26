package com.qiandaizi.app.ui.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qiandaizi.app.core.AiParseDto
import com.qiandaizi.app.core.AiParseReq
import com.qiandaizi.app.core.AiStatusDto
import com.qiandaizi.app.core.AppGraph
import com.qiandaizi.app.core.FlowDto
import com.qiandaizi.app.core.FlowReq
import com.qiandaizi.app.core.TextMain
import com.qiandaizi.app.core.TextSub
import com.qiandaizi.app.core.addMonth
import com.qiandaizi.app.core.explainError
import com.qiandaizi.app.core.money
import com.qiandaizi.app.core.monthCn
import com.qiandaizi.app.core.nowMonth
import com.qiandaizi.app.core.today
import com.qiandaizi.app.ui.common.GhostButton
import com.qiandaizi.app.ui.common.Pill
import com.qiandaizi.app.ui.common.WhiteCard
import com.qiandaizi.app.ui.record.FlowEditorSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val examples = listOf("午饭 35", "打车回家28", "发工资12000", "超市购物156.5微信", "收到红包200")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen() {
    val appState = AppGraph.state
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var text by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiParseDto?>(null) }
    var saving by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<FlowDto?>(null) }
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<AiStatusDto?>(null) }

    // 月度分析
    var month by rememberSaveable { mutableStateOf(nowMonth()) }
    var analyzing by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf("") }
    var summaryIn by remember { mutableStateOf(0.0) }
    var summaryEx by remember { mutableStateOf(0.0) }
    var summaryBa by remember { mutableStateOf(0.0) }
    var hasSummary by remember { mutableStateOf(false) }

    // 图片
    var imgDataUrl by remember { mutableStateOf<String?>(null) }
    var imgText by remember { mutableStateOf("") }
    var imgParsing by remember { mutableStateOf(false) }
    var imgOcrType by remember { mutableStateOf("") }
    // 图片识别结果独立展示在「图片记账」卡片内，不与上方一句话记账共用
    var imgResult by remember { mutableStateOf<AiParseDto?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { appState.api().aiStatus() }.onSuccess { status = it }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                        val mime = resolver.getType(uri) ?: "image/jpeg"
                        "data:$mime;base64," +
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }.onSuccess { imgDataUrl = it }
                    .onFailure { appState.notify("读取图片失败") }
            }
        }
    }

    // 拍照识别：TakePicture 契约 + FileProvider（App 未声明 CAMERA 权限，无需运行时申请）
    // 注意用 rememberSaveable：相机打开期间进程可能被回收/旋转，remember 会丢 uri 导致拍完没反应
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                        val mime = resolver.getType(uri) ?: "image/jpeg"
                        "data:$mime;base64," +
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }.onSuccess { imgDataUrl = it }
                    .onFailure { appState.notify("读取照片失败") }
            }
        }
    }

    fun launchCamera() {
        runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            pendingCameraUri = uri
            takePicture.launch(uri)
        }.onFailure { appState.notify("无法启动相机：${it.message ?: "未知错误"}") }
    }

    // 识别结果确认入账：一句话 / 图片两条路径共用
    fun confirmParse(r: AiParseDto, clear: () -> Unit) {
        val amount = r.amount ?: 0.0
        if (amount <= 0) {
            appState.notify("金额无效，无法记账")
            return
        }
        saving = true
        scope.launch {
            runCatching {
                appState.api().createFlow(
                    FlowReq(
                        type = r.type ?: "expense",
                        amount = amount,
                        category = r.category ?: "其他",
                        paymentMethod = r.paymentMethod?.ifBlank { null },
                        description = (r.description?.ifBlank { null }) ?: r.category,
                        flowTime = today(),
                        source = "ai"
                    )
                )
            }.onSuccess { idResp ->
                created = FlowDto(
                    id = idResp.id,
                    type = r.type ?: "expense",
                    amount = amount,
                    category = r.category ?: "其他",
                    paymentMethod = r.paymentMethod ?: "",
                    description = r.description ?: "",
                    flowTime = today()
                )
                clear()
                appState.bump()
                appState.notify("记账成功")
            }.onFailure { appState.notify(explainError(it)) }
            saving = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F9))
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(com.qiandaizi.app.core.Yellow)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 8.dp, bottom = 22.dp)
        ) {
            Text("AI 记账", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(4.dp))
            Text("一句话就能记账，智能识别金额、分类和支付方式",
                fontSize = 13.sp, color = Color(0xFF7A6520))
        }

        Column(Modifier.padding(14.dp)) {

            // ===== 图片记账 =====
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, contentDescription = null,
                        tint = Color(0xFF8A6D1B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("图片记账", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("拍照 / 上传小票或账单截图，自动识别金额、分类与名称",
                    fontSize = 12.sp, color = TextSub)
                Spacer(Modifier.height(12.dp))

                // OCR 类型选择（自动 = 走后端降级链）
                val ocrTypeOptions = listOf(
                    "" to "自动",
                    "general_basic" to "标准",
                    "accurate_basic" to "高精度",
                    "webimage" to "网络图",
                    "general" to "含位置",
                    "handwriting" to "手写"
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    ocrTypeOptions.forEach { (id, label) ->
                        val selected = imgOcrType == id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) com.qiandaizi.app.core.Yellow
                                    else Color(0xFFF6F7F9)
                                )
                                .clickable { imgOcrType = id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label, fontSize = 12.sp,
                                color = if (selected) TextMain else TextSub
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 相册选择
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF6F7F9))
                            .clickable {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val dataUrl = imgDataUrl
                        if (dataUrl != null) {
                            val bitmap = remember(dataUrl) {
                                val bytes = Base64.decode(
                                    dataUrl.substringAfter(","), Base64.DEFAULT
                                )
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Image, contentDescription = null,
                                    tint = TextSub, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("相册选择", fontSize = 12.sp, color = TextSub)
                            }
                        }
                    }
                    // 相机拍照
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFCF4DC))
                            .clickable { launchCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null,
                                tint = Color(0xFF8A6D1B), modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("拍照识别", fontSize = 12.sp, color = Color(0xFF8A6D1B))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = imgText,
                        onValueChange = { imgText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("补充说明（可选）", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (imgDataUrl == null) {
                                    appState.notify("请先选择一张小票 / 账单图片")
                                    return@Button
                                }
                                imgParsing = true
                                imgResult = null
                                scope.launch {
                                    runCatching {
                                        appState.api().aiParseImage(
                                            com.qiandaizi.app.core.AiParseImageReq(
                                                image = imgDataUrl,
                                                text = imgText.ifBlank { null },
                                                ocrType = imgOcrType.ifBlank { null }
                                            )
                                        )
                                    }.onSuccess {
                                        imgResult = it
                                        imgDataUrl = null
                                        imgText = ""
                                    }.onFailure { appState.notify(explainError(it)) }
                                    imgParsing = false
                                }
                            },
                            enabled = !imgParsing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.qiandaizi.app.core.Yellow
                            )
                        ) {
                            Text(if (imgParsing) "识别中…" else "识别并记账",
                            color = TextMain, fontSize = 13.sp)
                        }
                    }

                // 图片识别结果（展示在本卡片内）
                imgResult?.let { r ->
                    ParseResultCard(
                        r = r,
                        saving = saving,
                        onCancel = { imgResult = null },
                        onConfirm = { confirmParse(r) { imgResult = null } }
                    )
                }
                }

            // ===== 一句话记账 =====
            Spacer(Modifier.height(14.dp))
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null,
                        tint = Color(0xFF8A6D1B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("一句话记账", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (status?.enabled == true) "已接入 AI 大模型，识别更聪明"
                    else "未配置 AI，使用本地规则识别（在「更多 - AI设置」配置模型）",
                    fontSize = 12.sp, color = TextSub
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("午饭 35 / 发工资 12000", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            if (text.isBlank()) {
                                appState.notify("说点什么，比如「午饭35」")
                                return@Button
                            }
                            parsing = true
                            result = null
                            scope.launch {
                                runCatching { appState.api().aiParse(AiParseReq(text.trim())) }
                                    .onSuccess { result = it }
                                    .onFailure { appState.notify(explainError(it)) }
                                parsing = false
                            }
                        },
                        enabled = !parsing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.qiandaizi.app.core.Yellow),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(if (parsing) "…" else "识别", color = TextMain,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    examples.forEach { e ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF6F7F9))
                                .clickable {
                                    text = e
                                    parsing = true
                                    result = null
                                    scope.launch {
                                        runCatching { appState.api().aiParse(AiParseReq(e)) }
                                            .onSuccess { result = it }
                                            .onFailure { appState.notify(explainError(it)) }
                                        parsing = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(e, fontSize = 12.sp, color = TextSub)
                        }
                    }
                }

                // 识别结果
                result?.let { r ->
                    ParseResultCard(
                        r = r,
                        saving = saving,
                        onCancel = { result = null },
                        onConfirm = { confirmParse(r) { result = null; text = "" } }
                    )
                }

                // 已记账结果
                created?.let { c ->
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF1FBF6))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                if (c.type == "income") "收入" else "支出",
                                if (c.type == "income") com.qiandaizi.app.core.IncomeGreen
                                else com.qiandaizi.app.core.ExpenseRed
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(money(c.amount), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = if (c.type == "income") com.qiandaizi.app.core.IncomeGreen
                                else com.qiandaizi.app.core.ExpenseRed)
                            Spacer(Modifier.size(8.dp))
                            Text("✓ 已记账", fontSize = 12.sp,
                                color = com.qiandaizi.app.core.IncomeGreen)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            GhostButton("删除记账", onClick = {
                                scope.launch {
                                    runCatching { appState.api().deleteFlow(c.id) }
                                        .onSuccess {
                                            created = null
                                            appState.bump()
                                            appState.notify("已删除")
                                        }
                                        .onFailure { appState.notify(explainError(it)) }
                                }
                            })
                            Spacer(Modifier.size(10.dp))
                            Button(
                                onClick = { editing = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.qiandaizi.app.core.Yellow
                                )
                            ) { Text("修改记账", color = TextMain, fontSize = 13.sp) }
                        }
                    }
                }
            }

            // ===== 月度智能分析 =====
            Spacer(Modifier.height(14.dp))
            WhiteCard {
                Text("📊 月度智能分析", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF6F7F9))
                            .clickable {
                                month = addMonth(month, -1)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("‹", fontSize = 18.sp, color = com.qiandaizi.app.core.YellowDark) }
                    Text(monthCn(month), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = TextMain, modifier = Modifier.weight(2f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF6F7F9))
                            .clickable { month = addMonth(month, 1) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("›", fontSize = 18.sp, color = com.qiandaizi.app.core.YellowDark) }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        analyzing = true
                        scope.launch {
                            runCatching { appState.api().aiAnalyze(month) }
                                .onSuccess { d ->
                                    analysis = d.analysis
                                    d.summary?.let { s ->
                                        summaryIn = s["income"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                                        summaryEx = s["expense"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                                        summaryBa = s["balance"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                                        hasSummary = true
                                    }
                                }
                                .onFailure { appState.notify(explainError(it)) }
                            analyzing = false
                        }
                    },
                    enabled = !analyzing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.qiandaizi.app.core.Yellow)
                ) {
                    Text(if (analyzing) "分析中…" else "生成分析", color = TextMain,
                        fontWeight = FontWeight.SemiBold)
                }

                if (hasSummary) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        AnalysisCell("收入", money(summaryIn),
                            com.qiandaizi.app.core.IncomeGreen, Modifier.weight(1f))
                        AnalysisCell("支出", money(summaryEx),
                            com.qiandaizi.app.core.ExpenseRed, Modifier.weight(1f))
                        AnalysisCell("结余", money(summaryBa),
                            if (summaryBa >= 0) com.qiandaizi.app.core.IncomeGreen
                            else com.qiandaizi.app.core.ExpenseRed, Modifier.weight(1f))
                    }
                }
                if (analysis.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        analysis,
                        fontSize = 13.sp,
                        color = Color(0xFF444444),
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8F9FB))
                            .padding(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (editing && created != null) {
        FlowEditorSheet(
            flow = created!!,
            categories = emptyList(),
            onDismiss = { editing = false },
            onChanged = {
                created = null
                editing = false
            }
        )
    }
}

@Composable
private fun AnalysisCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = TextSub)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color,
            modifier = Modifier.padding(top = 4.dp))
    }
}

// 识别结果卡片：一句话记账 / 图片记账共用
@Composable
private fun ParseResultCard(
    r: AiParseDto,
    saving: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8F9FB))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val isExpense = r.type != "income"
            Pill(
                if (isExpense) "支出" else "收入",
                if (isExpense) com.qiandaizi.app.core.ExpenseRed
                else com.qiandaizi.app.core.IncomeGreen
            )
            Spacer(Modifier.size(8.dp))
            Text(
                money(r.amount),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) com.qiandaizi.app.core.ExpenseRed
                else com.qiandaizi.app.core.IncomeGreen
            )
            Spacer(Modifier.size(8.dp))
            r.category?.let { Pill(it, Color(0xFFE9EBF0), TextMain) }
            Spacer(Modifier.size(6.dp))
            r.paymentMethod?.takeIf { it.isNotBlank() }?.let {
                Pill(it, Color(0xFFE9EBF0), TextMain)
            }
        }
        if (!r.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("名称：${r.description}", fontSize = 13.sp, color = TextSub)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "来源：" + when (r.source) {
                "ai" -> "AI模型"
                "ocr" -> "百度OCR" + (r.ocrType?.let { "·$it" } ?: "")
                else -> "本地规则"
            },
            fontSize = 11.sp, color = TextSub
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton("取消", onClick = onCancel)
            Spacer(Modifier.size(10.dp))
            Button(
                onClick = onConfirm,
                enabled = !saving,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.qiandaizi.app.core.Yellow
                )
            ) {
                Text("确认并记账", color = TextMain, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
