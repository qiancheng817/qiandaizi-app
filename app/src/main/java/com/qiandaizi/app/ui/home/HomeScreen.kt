package com.qiandaizi.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiandaizi.app.core.AppGraph
import com.qiandaizi.app.core.AttrMember
import com.qiandaizi.app.core.BudgetDataDto
import com.qiandaizi.app.core.CategoryDto
import com.qiandaizi.app.core.DailyDto
import com.qiandaizi.app.core.FlowDto
import com.qiandaizi.app.core.HomePrefetcher
import com.qiandaizi.app.core.MoneyBagsDto
import com.qiandaizi.app.core.TextMain
import com.qiandaizi.app.core.TextSub
import com.qiandaizi.app.core.addMonth
import com.qiandaizi.app.core.amount
import com.qiandaizi.app.core.doneValue
import com.qiandaizi.app.core.explainError
import com.qiandaizi.app.core.md
import com.qiandaizi.app.core.money
import com.qiandaizi.app.core.monthCn
import com.qiandaizi.app.core.nowMonth
import com.qiandaizi.app.ui.common.EmptyHint
import com.qiandaizi.app.ui.common.LoadingBox
import com.qiandaizi.app.ui.common.WhiteCard
import com.qiandaizi.app.ui.common.AppIcon
import com.qiandaizi.app.ui.record.FlowEditorSheet
import kotlinx.coroutines.launch
import kotlin.math.min

private val BubbleCream = Color(0xFFFFFBE8)
private val SoftGrayBox = Color(0xFFF3F4F6)

@Composable
fun HomeScreen() {
    val appState = AppGraph.state
    val scope = rememberCoroutineScope()

    var month by rememberSaveable { mutableStateOf(nowMonth()) }
    var bagIndex by rememberSaveable { mutableStateOf(0) } // 默认总钱袋
    var calOpen by rememberSaveable { mutableStateOf(false) }
    var monthDialog by remember { mutableStateOf(false) }

    // 冷启动开屏阶段已等待预取完成，这里一次性取走快照；
    // 已完成的结果直接作为 state 初始值，首帧即真实内容，无 loading→内容跳变。
    // 切月/刷新后为 null，走正常网络请求
    val prefetch = remember(month) { HomePrefetcher.consume(month) }

    var bags by remember { mutableStateOf(prefetch?.bags?.doneValue()) }
    var calendar by remember {
        mutableStateOf(prefetch?.calendar?.doneValue() ?: emptyList())
    }
    var members by remember {
        mutableStateOf(prefetch?.members?.doneValue()?.members ?: emptyList())
    }
    val flowsReady = prefetch?.flows?.isCompleted == true
    var recent by remember {
        mutableStateOf(prefetch?.flows?.doneValue()?.list ?: emptyList())
    }
    var recentFilter by rememberSaveable { mutableStateOf("all") }
    var budget by remember { mutableStateOf(prefetch?.budgets?.doneValue()) }
    var categories by remember {
        mutableStateOf(prefetch?.categories?.doneValue() ?: emptyList())
    }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 预取流水已就绪则首帧不转圈；否则显示 loading，且未加载完不渲染"无记录"空态
    var recentLoading by remember { mutableStateOf(!flowsReady) }
    var recentLoaded by remember { mutableStateOf(flowsReady) }

    var searchOpen by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FlowDto>>(emptyList()) }
    var searchDone by remember { mutableStateOf(false) }

    var editing by remember { mutableStateOf<FlowDto?>(null) }

    val me = appState.user()
    val other = members.firstOrNull { it.id != me?.id }
    // 三个钱袋固定存在（默认总钱袋）；对方暂无成员时数据按 0 展示
    val bagTitles = listOf("总钱袋", "我的钱袋", "对方的钱袋")

    // 账本成员（独立加载，不被其他接口失败拖累）
    LaunchedEffect(appState.epoch) {
        runCatching {
            prefetch?.members?.await()?.getOrThrow() ?: appState.api().attributions()
        }.onSuccess { members = it.members }
    }

    // 钱袋数据
    LaunchedEffect(month, appState.epoch) {
        loadError = null
        runCatching {
            prefetch?.bags?.await()?.getOrThrow() ?: appState.api().moneybags(month)
        }
            .onSuccess { bags = it }
            .onFailure { loadError = explainError(it) }
    }

    // 日历热力
    LaunchedEffect(month, appState.epoch) {
        runCatching {
            prefetch?.calendar?.await()?.getOrThrow() ?: appState.api().statCalendar(month)
        }
            .onSuccess { calendar = it }
    }

    // 分类
    LaunchedEffect(appState.epoch) {
        runCatching {
            prefetch?.categories?.await()?.getOrThrow() ?: appState.api().categories()
        }
            .onSuccess { categories = it }
    }

    // 最近记录
    LaunchedEffect(month, recentFilter, appState.epoch) {
        recentLoading = true
        // 预取只覆盖默认「全部」筛选；切到成员筛选时正常请求
        val prefetchedFlows = if (recentFilter == "all") prefetch?.flows else null
        runCatching {
            val (start, end) = com.qiandaizi.app.core.monthRange(month)
            val attrUid = when (recentFilter) {
                "me" -> me?.id
                "other" -> other?.id
                else -> null
            }
            prefetchedFlows?.await()?.getOrThrow()
                ?: appState.api().flows(
                    start = start,
                    end = end,
                    attributionUid = attrUid,
                    page = 1,
                    pageSize = 300
                )
        }.onSuccess { recent = it.list }
        recentLoading = false
        recentLoaded = true
    }

    // 年预算
    LaunchedEffect(month, appState.epoch) {
        runCatching {
            prefetch?.budgets?.await()?.getOrThrow()
                ?: appState.api().budgets(month.substring(0, 4).toInt())
        }.onSuccess { budget = it }
    }

    // 搜索
    LaunchedEffect(keyword) {
        if (searchOpen && keyword.isNotBlank()) {
            runCatching { appState.api().flows(keyword = keyword, pageSize = 30) }
                .onSuccess {
                    searchResults = it.list
                    searchDone = true
                }
        } else {
            searchResults = emptyList()
            searchDone = false
        }
    }

    // 最近记录按日期分组（日期倒序），同时汇总当日收支
    val groupedRecent = recent
        .groupBy { it.flowTime.take(10) }
        .toSortedMap(reverseOrder())
        .map { (date, items) ->
            DayGroup(
                date = date,
                dayExpense = items.filter { it.type == "expense" }.sumOf { it.amount },
                dayIncome = items.filter { it.type == "income" }.sumOf { it.amount },
                items = items
            )
        }

    Column(
        Modifier
            .fillMaxSize()
            .background(com.qiandaizi.app.core.AppBg)
            .verticalScroll(rememberScrollState())
    ) {
        // ===== 黄色顶部 =====
        Column(
            Modifier
                .fillMaxWidth()
                .background(com.qiandaizi.app.core.Yellow)
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BubbleSwitcher(
                    titles = bagTitles,
                    index = bagIndex,
                    onCycle = { delta ->
                        bagIndex = (bagIndex + delta + bagTitles.size) % bagTitles.size
                    }
                )
                Spacer(Modifier.weight(1f))
                // 搜索账单胶囊
                Row(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCCFFFFFF))
                        .clickable {
                            searchOpen = !searchOpen
                            if (!searchOpen) keyword = ""
                        }
                        .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (searchOpen) "收起搜索" else "搜索账单",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 14.dp)) {

            // 主卡片与黄色顶栏之间留出底色间隔
            Spacer(Modifier.height(12.dp))

            if (searchOpen) {
                WhiteCard(modifier = Modifier.padding(bottom = 14.dp)) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入关键词搜索全部账单", color = TextSub) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = TextSub
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        keyword.isBlank() -> Text("输入后自动搜索", fontSize = 13.sp, color = TextSub)
                        searchDone && searchResults.isEmpty() ->
                            Text("没有找到相关账单", fontSize = 13.sp, color = TextSub)
                        else -> searchResults.take(20).forEach { f ->
                            val icon = categories.firstOrNull { it.name == f.category }?.icon ?: "💰"
                            FlowRowSimple(f, icon = icon) { editing = f }
                        }
                    }
                }
            }

            // ===== 主卡片（总钱袋/我的/对方 左右滑动切换，三页同高） =====
            WhiteCard {
                if (bags == null) {
                    if (loadError != null) {
                        Text(loadError!!, fontSize = 13.sp, color = TextSub)
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(190.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = com.qiandaizi.app.core.YellowDark
                            )
                        }
                    }
                } else {
                    val data = bags!!

                    val pagerState = rememberPagerState(
                        initialPage = bagIndex.coerceIn(0, bagTitles.lastIndex)
                    ) { bagTitles.size }
                    // 箭头/外部改 bagIndex → 滚动到对应页
                    LaunchedEffect(bagIndex) {
                        if (pagerState.currentPage != bagIndex) {
                            pagerState.animateScrollToPage(bagIndex)
                        }
                    }
                    // 手指滑动 → 同步 bagIndex（月份标签/筛选随之变化）
                    LaunchedEffect(pagerState.currentPage) {
                        if (bagIndex != pagerState.currentPage) {
                            bagIndex = pagerState.currentPage
                        }
                    }

                    // 顶部行：月份 + 日历 + 同步
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(com.qiandaizi.app.core.YellowSoft)
                                .clickable(enabled = bagTitles[bagIndex] != "总钱袋") {
                                    monthDialog = true
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                if (bagTitles[bagIndex] == "总钱袋") "全部记录"
                                else monthCn(month),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMain
                            )
                            if (bagTitles[bagIndex] != "总钱袋") {
                                Text("  ›", fontSize = 15.sp, color = TextMain)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        CircleIconBtn(
                            icon = Icons.Filled.CalendarMonth,
                            active = calOpen,
                            onClick = { calOpen = !calOpen }
                        )
                        Spacer(Modifier.size(10.dp))
                        CircleIconBtn(
                            icon = Icons.Filled.Sync,
                            active = appState.syncing,
                            spin = appState.syncing,
                            onClick = {
                                scope.launch {
                                    if (appState.sync()) appState.notify("已与服务器同步")
                                    else appState.notify("同步失败：无法连接服务器")
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // 三钱袋数字区：横向滑动，三页结构完全一致（同高）
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val meBucket = data.buckets.find { it.uid == me?.id }
                        val otherBucket = data.buckets.find { it.uid != null && it.uid != me?.id }

                        val expense: Double
                        val income: Double
                        val balance: Double
                        val yearBalance: Double?
                        when (bagTitles[page]) {
                            "我的钱袋" -> {
                                expense = meBucket?.monthExpense ?: 0.0
                                income = meBucket?.monthIncome ?: 0.0
                                balance = income - expense
                                yearBalance = (meBucket?.yearIncome ?: 0.0) -
                                    (meBucket?.yearExpense ?: 0.0)
                            }
                            "对方的钱袋" -> {
                                expense = otherBucket?.monthExpense ?: 0.0
                                income = otherBucket?.monthIncome ?: 0.0
                                balance = income - expense
                                yearBalance = (otherBucket?.yearIncome ?: 0.0) -
                                    (otherBucket?.yearExpense ?: 0.0)
                            }
                            else -> {
                                expense = data.total.expense
                                income = data.total.income
                                balance = data.total.balance
                                yearBalance = null
                            }
                        }

                        Column {
                            // 支
                            AmountRow(
                                badge = "支",
                                value = "¥${amount(expense)}",
                                valueColor = com.qiandaizi.app.core.BrandBlue,
                                big = true
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AmountRow(
                                    badge = "收",
                                    value = "¥${amount(income)}",
                                    valueColor = com.qiandaizi.app.core.ExpenseRed,
                                    big = true,
                                    baseFontSize = 18f,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.size(12.dp))
                                AmountRow(
                                    badge = "余",
                                    value = (if (balance >= 0) "" else "-") +
                                        "¥${amount(kotlin.math.abs(balance))}",
                                    valueColor = com.qiandaizi.app.core.IncomeGreen,
                                    big = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 年结余：总钱袋无此数据，用同结构透明行占位，保证三页等高
                            Spacer(Modifier.height(12.dp))
                            if (yearBalance != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${data.year}年结余",
                                        fontSize = 13.sp,
                                        color = TextSub
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        (if (yearBalance >= 0) "" else "-") +
                                            money(kotlin.math.abs(yearBalance)),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (yearBalance >= 0)
                                            com.qiandaizi.app.core.IncomeGreen
                                        else com.qiandaizi.app.core.ExpenseRed
                                    )
                                }
                            } else {
                                Text(" ", fontSize = 14.sp)
                            }
                        }
                    }

                    // 滑动指示点
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        bagTitles.indices.forEach { i ->
                            val selected = i == pagerState.currentPage
                            Box(
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (selected) 7.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) com.qiandaizi.app.core.YellowDark
                                        else Color(0xFFDDDDDD)
                                    )
                            )
                        }
                    }

                    // 年预算进度
                    val b = budget
                    if (b != null && b.total.amount > 0) {
                        Spacer(Modifier.height(14.dp))
                        BudgetBar(b)
                    }

                    // 消费日历
                    if (calOpen) {
                        Spacer(Modifier.height(18.dp))
                        CalendarHeatmap(
                            month = month,
                            calendar = calendar,
                            onChangeMonth = { delta ->
                                month = addMonth(month, delta)
                            }
                        )
                    }
                }
            }

            // ===== 最近记录（按日期分隔） =====
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近记录",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    modifier = Modifier.weight(1f)
                )
                MiniFilter(
                    options = listOf("all" to "所有", "me" to "我的", "other" to "对方"),
                    selected = recentFilter,
                    enabled = if (other != null) setOf("all", "me", "other") else setOf("all", "me"),
                    onSelect = { recentFilter = it }
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                recentLoading -> WhiteCard {
                    Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = com.qiandaizi.app.core.YellowDark
                        )
                    }
                }
                groupedRecent.isEmpty() && recentLoaded -> WhiteCard {
                    Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "本月还没有相关记录，点底部 ＋ 开始记一笔吧",
                            fontSize = 13.sp,
                            color = TextSub
                        )
                    }
                }
                else -> groupedRecent.forEachIndexed { idx, group ->
                    if (idx > 0) Spacer(Modifier.height(10.dp))
                    WhiteCard(padding = 14) {
                        DayHeader(group)
                        group.items.forEachIndexed { i, f ->
                            if (i > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFF2F3F5))
                                )
                            }
                            val icon = categories.firstOrNull { it.name == f.category }?.icon ?: "💰"
                            FlowRowSimple(f, icon = icon) { editing = f }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    editing?.let { f ->
        FlowEditorSheet(
            flow = f,
            categories = categories,
            onDismiss = { editing = null },
            onChanged = {}
        )
    }

    if (monthDialog) {
        MonthPickerDialog(
            month = month,
            onPick = {
                month = it
                monthDialog = false
            },
            onDismiss = { monthDialog = false }
        )
    }
}

/* ==================== 气泡切换器（图2） ==================== */

@Composable
private fun BubbleSwitcher(
    titles: List<String>,
    index: Int,
    onCycle: (Int) -> Unit
) {
    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(BubbleCream)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                fontSize = 22.sp,
                color = com.qiandaizi.app.core.YellowDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onCycle(-1) },
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(4.dp))
            AppIcon(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.size(8.dp))
            Text(
                titles[index],
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF555555)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "›",
                fontSize = 22.sp,
                color = com.qiandaizi.app.core.YellowDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onCycle(1) },
                textAlign = TextAlign.Center
            )
        }
        // 小三角
        Canvas(Modifier.padding(start = 30.dp).size(12.dp, 8.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(path, BubbleCream)
        }
    }
}

/* ==================== 小组件 ==================== */

@Composable
private fun CircleIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    spin: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) com.qiandaizi.app.core.YellowSoft else SoftGrayBox)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color(0xFF8A6D1B) else Color(0xFF8A8F98),
            modifier = Modifier
                .size(20.dp)
                .then(if (spin) Modifier else Modifier)
        )
    }
}

@Composable
private fun AmountRow(
    badge: String,
    value: String,
    valueColor: Color,
    big: Boolean,
    modifier: Modifier = Modifier,
    baseFontSize: Float = if (big) 24f else 18f
) {
    // 字号自适应：在组合阶段用 TextMeasurer 预算不溢出的最大字号，
    // 避免「先按大字号绘制一帧、onTextLayout 后再回缩重绘」导致的金额闪动
    BoxWithConstraints(modifier) {
        val badgeSize = if (big) 42.dp else 38.dp
        val textMaxWidth = (maxWidth - badgeSize - 12.dp).coerceAtLeast(1.dp)
        val maxWidthPx = with(LocalDensity.current) { textMaxWidth.roundToPx() }
        val measurer = rememberTextMeasurer()
        val baseStyle = remember(valueColor) {
            TextStyle(fontWeight = FontWeight.Bold, color = valueColor)
        }

        var fitted = baseFontSize
        while (fitted > 10f) {
            val result = measurer.measure(
                text = value,
                style = baseStyle.copy(fontSize = fitted.sp),
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
            if (!result.hasVisualOverflow) break
            fitted -= 1f
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(badgeSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftGrayBox),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge,
                    fontSize = if (big) 16.sp else 15.sp,
                    color = Color(0xFF777C85),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                value,
                fontSize = fitted.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun BudgetBar(b: BudgetDataDto) {
    val over = b.total.remaining < 0
    val percent = if (b.total.amount > 0)
        (b.total.spent / b.total.amount).toFloat() else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFAFAFA))
            .padding(14.dp)
    ) {
        Row {
            Text(
                if (over) "今年预算已超支 " else "今年预算已用 ",
                fontSize = 14.sp,
                color = TextMain
            )
            Text(
                money(if (over) -b.total.remaining else b.total.spent),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) com.qiandaizi.app.core.ExpenseRed
                else com.qiandaizi.app.core.YellowDark
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFEEEEEE))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(min(percent, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (over) com.qiandaizi.app.core.ExpenseRed
                        else com.qiandaizi.app.core.Yellow
                    )
            )
        }
    }
}

@Composable
private fun MiniFilter(
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Set<String>,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFF2F3F5))
            .padding(3.dp)
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            val clickable = key in enabled
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable(enabled = clickable) { onSelect(key) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (!clickable) Color(0xFFC8CBD0)
                    else if (active) com.qiandaizi.app.core.YellowDark else TextSub,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun FlowRowSimple(f: FlowDto, icon: String = "💰", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F6F8)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (f.source == "ai") {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(com.qiandaizi.app.core.YellowSoft)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "AI",
                            fontSize = 9.sp,
                            color = Color(0xFF8A6D1B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.size(5.dp))
                }
                Text(
                    f.category + (if (f.description.isNotBlank()) " · ${f.description}" else ""),
                    fontSize = 14.sp,
                    color = TextMain,
                    maxLines = 1
                )
            }
            Text(
                "${md(f.flowTime)} · ${f.attribution.ifBlank { "—" }}",
                fontSize = 12.sp,
                color = TextSub,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            (if (f.type == "income") "+" else "-") + amount(f.amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (f.type == "income") com.qiandaizi.app.core.IncomeGreen
            else TextMain
        )
    }
}

/* ==================== 消费日历热力图 ==================== */

@Composable
private fun CalendarHeatmap(
    month: String,
    calendar: List<DailyDto>,
    onChangeMonth: (Int) -> Unit
) {
    val calMap = calendar.associateBy { it.date }
    val maxExpense = calendar.maxOfOrNull { it.expense } ?: 1.0

    val first = java.time.LocalDate.parse("${month}-01")
    val leadingBlanks = first.dayOfWeek.value % 7 // 周日=0
    val daysInMonth = first.lengthOfMonth()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 18.sp, color = TextMain,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChangeMonth(-1) },
                textAlign = TextAlign.Center)
            Text(
                monthCn(month),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text("›", fontSize = 18.sp, color = TextMain,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChangeMonth(1) },
                textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Text(
                    w, fontSize = 12.sp, color = TextSub,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val cells = buildList {
            repeat(leadingBlanks) { add(null) }
            for (d in 1..daysInMonth) {
                add("${month}-%02d".format(d))
            }
        }.chunked(7)

        cells.forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (i in 0 until 7) {
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val day = calMap[date]
                            val heat = if (day != null && day.expense > 0)
                                min(1.0, day.expense / maxExpense).toFloat() else 0f
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            if (heat > 0) com.qiandaizi.app.core.ExpenseRed.copy(
                                                alpha = 0.12f + heat * 0.55f
                                            ) else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        date.substring(8).trimStart('0'),
                                        fontSize = 12.sp,
                                        color = TextMain
                                    )
                                }
                                if (day != null && day.expense > 0) {
                                    Text(
                                        "-${day.expense.toInt()}",
                                        fontSize = 8.sp,
                                        color = com.qiandaizi.app.core.ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ==================== 月份选择弹窗 ==================== */

@Composable
private fun MonthPickerDialog(
    month: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var m by remember { mutableStateOf(month) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹", fontSize = 22.sp, color = TextMain,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { m = addMonth(m, -1) },
                    textAlign = TextAlign.Center
                )
                Text(
                    monthCn(m),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "›", fontSize = 22.sp, color = TextMain,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { m = addMonth(m, 1) },
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            TextButton(onClick = { m = nowMonth() }, modifier = Modifier.fillMaxWidth()) {
                Text("回到本月", color = com.qiandaizi.app.core.YellowDark)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(m) }) {
                Text("确定", color = com.qiandaizi.app.core.YellowDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSub) }
        }
    )
}

/* ==================== 日期分组 ==================== */

private data class DayGroup(
    val date: String,
    val dayExpense: Double,
    val dayIncome: Double,
    val items: List<FlowDto>
)

private val WeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
private fun DayHeader(group: DayGroup) {
    val localDate = java.time.LocalDate.parse(group.date)
    val title = "${localDate.monthValue}.${localDate.dayOfMonth} " +
        WeekNames[(localDate.dayOfWeek.value - 1).coerceIn(0, 6)]

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 左侧黄色竖条
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(com.qiandaizi.app.core.YellowDark)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextMain
        )
        Spacer(Modifier.size(6.dp))
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = Color(0xFF9AA0AA),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.weight(1f))
        if (group.dayExpense > 0) {
            DayTotalBadge("支", amount(group.dayExpense),
                Color(0xFFFDECEC), com.qiandaizi.app.core.ExpenseRed)
        }
        if (group.dayIncome > 0) {
            Spacer(Modifier.size(6.dp))
            DayTotalBadge("收", amount(group.dayIncome),
                Color(0xFFEAF1FF), com.qiandaizi.app.core.BrandBlue)
        }
    }
}

@Composable
private fun DayTotalBadge(
    tag: String,
    value: String,
    bg: Color,
    fg: Color
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tag, fontSize = 11.sp, color = fg, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(4.dp))
        Text(value, fontSize = 11.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

