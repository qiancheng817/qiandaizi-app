package com.qiandaizi.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 首页冷启动预取（纯内存，不落盘）：
 *
 * 开屏阶段会话一旦就绪，立刻在后台并发发起首页所需的 6 个请求；
 * HomeScreen 首次组合时直接 await 这批「在途请求」，不再重复发请求，
 * 从而把请求耗时藏进开屏的 1.4s 里。进程被杀后预取自然消失，
 * 手机本地不保存任何账单数据。
 *
 * 预取结果一次性消费：记账/切月等 epoch 刷新后走正常网络请求。
 */
object HomePrefetcher {

    class Snapshot(
        val month: String,
        val members: Deferred<Result<AttrListDto>>,
        val bags: Deferred<Result<MoneyBagsDto>>,
        val calendar: Deferred<Result<List<DailyDto>>>,
        val categories: Deferred<Result<List<CategoryDto>>>,
        val flows: Deferred<Result<FlowPageDto>>,
        val budgets: Deferred<Result<BudgetDataDto>>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var snapshot: Snapshot? = null

    /** 会话（服务器/账号/账本）就绪后在开屏阶段调用，重复调用安全 */
    fun trigger() {
        if (snapshot != null) return
        val state = AppGraph.state
        if (!state.isReady) return
        val api = state.api()
        val month = nowMonth()
        val (start, end) = monthRange(month)
        val year = month.substring(0, 4).toInt()

        snapshot = Snapshot(
            month = month,
            members = scope.async { runCatching { api.attributions() } },
            bags = scope.async { runCatching { api.moneybags(month) } },
            calendar = scope.async { runCatching { api.statCalendar(month) } },
            categories = scope.async { runCatching { api.categories() } },
            flows = scope.async {
                runCatching {
                    api.flows(start = start, end = end, page = 1, pageSize = 300)
                }
            },
            budgets = scope.async { runCatching { api.budgets(year) } }
        )
    }

    /**
     * HomeScreen 首次展示某月份时取走预取快照（一次性）。
     * 月份不匹配（用户已切月）或已被消费时返回 null，调用方走正常请求。
     */
    fun consume(month: String): Snapshot? {
        val s = snapshot ?: return null
        if (s.month != month) return null
        snapshot = null
        return s
    }
}
