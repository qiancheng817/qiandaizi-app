package com.qiandaizi.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiandaizi.app.core.AppGraph
import com.qiandaizi.app.core.CardWhite
import com.qiandaizi.app.core.TextMain
import com.qiandaizi.app.core.TextSub
import com.qiandaizi.app.core.Yellow
import com.qiandaizi.app.core.YellowDark
import com.qiandaizi.app.core.explainError
import com.qiandaizi.app.ui.auth.LoginScreen
import com.qiandaizi.app.ui.auth.ServerScreen
import com.qiandaizi.app.ui.ai.AiScreen
import com.qiandaizi.app.ui.home.HomeScreen
import com.qiandaizi.app.ui.more.AboutScreen
import com.qiandaizi.app.ui.more.AccountScreen
import com.qiandaizi.app.ui.more.AdminUsersScreen
import com.qiandaizi.app.ui.more.AiSettingsScreen
import com.qiandaizi.app.ui.more.BooksScreen
import com.qiandaizi.app.ui.more.SwitchBookScreen
import com.qiandaizi.app.ui.more.BudgetsScreen
import com.qiandaizi.app.ui.more.CategoriesScreen
import com.qiandaizi.app.ui.more.MoreScreen
import com.qiandaizi.app.ui.more.OpLogsScreen
import com.qiandaizi.app.ui.more.PresetsScreen
import com.qiandaizi.app.ui.more.QuickAiScreen
import com.qiandaizi.app.ui.more.RecurringScreen
import com.qiandaizi.app.ui.more.SavingsScreen
import com.qiandaizi.app.ui.more.ServerSwitchScreen
import com.qiandaizi.app.ui.more.BillsScreen
import com.qiandaizi.app.ui.more.TrashScreen
import com.qiandaizi.app.ui.more.WalletDetailScreen
import com.qiandaizi.app.ui.more.WalletsScreen
import com.qiandaizi.app.ui.common.UpdateDialogs
import com.qiandaizi.app.ui.common.UpdateFlow
import com.qiandaizi.app.ui.common.AppIcon
import com.qiandaizi.app.ui.record.RecordScreen
import com.qiandaizi.app.ui.stats.StatsScreen
import kotlinx.coroutines.delay

/* ================= 子页面路由 ================= */

sealed interface Route {
    data object Books : Route
    data object SwitchBook : Route
    data object Categories : Route
    data object Budgets : Route
    data object Recurring : Route
    data object Savings : Route
    data object Wallets : Route
    data class WalletDetail(val id: Int, val name: String) : Route
    data object Presets : Route
    data object AdminUsers : Route
    data object AiSettings : Route
    data object Trash : Route
    data object OpLogs : Route
    data object Account : Route
    data object About : Route
    data object QuickAi : Route
    data object ServerSwitch : Route
    data object Bills : Route
}

/* ================= 开屏页 ================= */

/** 冷启动开屏：品牌黄底 + 图标淡入放大，衔接系统 Splash 到首帧内容 */
@Composable
private fun SplashGate() {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.82f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(600))
    }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(600))
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Yellow)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(
            Modifier
                .size(96.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .clip(RoundedCornerShape(26.dp))
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "钱袋子", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = TextMain, modifier = Modifier.alpha(alpha.value)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "一句话就能记账", fontSize = 13.sp, color = YellowDark,
            modifier = Modifier.alpha(alpha.value)
        )
    }
}

/* ================= 登录态闸门 ================= */

@Composable
fun RootGate() {
    val state = AppGraph.state

    // 开屏最短展示 1.4s：会话从本地读取太快时也能看到品牌页，不会一闪而过
    var splashMinDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_400)
        splashMinDone = true
    }

    Box(Modifier.fillMaxSize()) {
        when {
            !state.loaded || !splashMinDone -> SplashGate()
            state.server == null -> ServerScreen()
            state.account() == null -> LoginScreen()
            state.bookId() == null -> EnsureBookGate()
            else -> MainShell()
        }

        // 全局轻提示
        var shown by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.notice) {
            shown = state.notice
            if (shown != null) delay(2000)
            shown = null
        }
        if (shown != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 40.dp, end = 40.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC333333))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(shown!!, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

/* ================= 主壳 ================= */

private data class TabItem(
    val label: String,
    val icon: ImageVector? = null,
    val isCenter: Boolean = false
)

private val tabs = listOf(
    TabItem("首页", Icons.Filled.Home),
    TabItem("统计", Icons.Filled.PieChart),
    TabItem("记一笔", isCenter = true),
    TabItem("AI记账", Icons.Filled.AutoAwesome),
    TabItem("更多", Icons.Filled.Apps)
)

@Composable
fun MainShell() {
    var tab by remember { mutableIntStateOf(0) }
    var subStack by remember { mutableStateOf(listOf<Route>()) }

    val push: (Route) -> Unit = { subStack = subStack + it }
    val pop: () -> Unit = { if (subStack.isNotEmpty()) subStack = subStack.dropLast(1) }

    // 冷启动静默检查更新：延迟 3 秒，避免与首页加载抢资源；仅发现新版本时弹窗
    LaunchedEffect(Unit) {
        delay(3_000)
        UpdateFlow.silentCheck()
    }
    UpdateDialogs()

    // 系统返回（含屏幕左边缘右滑手势）：先退子页面，再回首页
    BackHandler(enabled = subStack.isNotEmpty()) { pop() }
    BackHandler(enabled = subStack.isEmpty() && tab != 0) { tab = 0 }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeScreen()
                1 -> StatsScreen()
                2 -> RecordScreen(
                    onBack = { tab = 0 },
                    onOpenSettings = { push(Route.Categories) }
                )
                3 -> AiScreen()
                else -> MoreScreen(onOpen = push)
            }
            subStack.lastOrNull()?.let { route ->
                SubRoute(route = route, onBack = pop, push = push)
            }
        }
        // 底栏随时可点：切换 Tab 时同时退出所有子页面
        BottomBar(tab = tab, onSelect = {
            tab = it
            subStack = emptyList()
        })
    }
}

/** 账本初始化闸门：自动补建账本，失败可重试 */
@Composable
private fun EnsureBookGate() {
    val state = AppGraph.state
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.epoch) {
        error = null
        runCatching { state.ensureBook() }
            .onFailure { error = explainError(it) }
    }

    Box(Modifier.fillMaxSize().background(Yellow), contentAlignment = Alignment.Center) {
        if (error == null) {
            androidx.compose.material3.CircularProgressIndicator(color = YellowDark)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 40.dp)
            ) {
                Text("账本初始化失败", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = TextMain)
                Spacer(Modifier.height(8.dp))
                Text(error!!, fontSize = 13.sp, color = TextSub,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                com.qiandaizi.app.ui.common.GhostButton("重试", onClick = { state.bump() })
            }
        }
    }
}

@Composable
private fun SubRoute(route: Route, onBack: () -> Unit, push: (Route) -> Unit) {
    when (route) {
        Route.Books -> BooksScreen(onBack)
        Route.SwitchBook -> SwitchBookScreen(onBack)
        Route.Categories -> CategoriesScreen(onBack)
        Route.Budgets -> BudgetsScreen(onBack)
        Route.Recurring -> RecurringScreen(onBack)
        Route.Savings -> SavingsScreen(onBack)
        Route.Wallets -> WalletsScreen(onBack, push)
        is Route.WalletDetail -> WalletDetailScreen(route.id, route.name, onBack)
        Route.Presets -> PresetsScreen(onBack)
        Route.AdminUsers -> AdminUsersScreen(onBack)
        Route.AiSettings -> AiSettingsScreen(onBack)
        Route.Trash -> TrashScreen(onBack)
        Route.OpLogs -> OpLogsScreen(onBack)
        Route.Account -> AccountScreen(onBack)
        Route.About -> AboutScreen(onBack)
        Route.QuickAi -> QuickAiScreen(onBack)
        Route.ServerSwitch -> ServerSwitchScreen(onBack)
        Route.Bills -> BillsScreen(onBack)
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    Surface(color = CardWhite, shadowElevation = 12.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { i, item ->
                if (item.isCenter) {
                    // 中间 +：与其他图标同排，不凸出，完整显示
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Yellow)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "记一笔",
                            tint = TextMain,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    val selected = tab == i
                    Column(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            item.icon!!,
                            contentDescription = item.label,
                            tint = if (selected) YellowDark else Color(0xFFB5B8BF),
                            modifier = Modifier.size(25.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) YellowDark else TextSub
                        )
                    }
                }
            }
        }
        }
    }
}
