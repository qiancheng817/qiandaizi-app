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
 * RootGate 会等待这批请求完成再进首页，HomeScreen 首帧即可同步取到结果，
 * 无 loading→内容的跳变。进程被杀后预取自然消失，手机本地不保存任何账单数据。
 *
 * 快照按「服务器+账号+账本」隔离，切换身份后旧快照自动作废；
 * 且一次性消费：记账/切月等 epoch 刷新后走正常网络请求。
 */
object HomePrefetcher {

    class Snapshot(
        val key: String,
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

    private fun currentKey(): String? {
        val state = AppGraph.state
        if (!state.isReady) return null
        return "${state.server}|${state.account()?.username}|${state.bookId()}"
    }

    /** 会话（服务器/账号/账本）就绪后在开屏阶段调用；身份变化会重新预取 */
    fun trigger() {
        val state = AppGraph.state
        if (!state.isReady) return
        val key = currentKey() ?: return
        if (snapshot?.key == key) return
        val api = state.api()
        val month = nowMonth()
        val (start, end) = monthRange(month)
        val year = month.substring(0, 4).toInt()

        snapshot = Snapshot(
            key = key,
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
     * 身份/月份不匹配或已被消费时返回 null，调用方走正常请求。
     */
    fun consume(month: String): Snapshot? {
        val s = snapshot ?: return null
        if (s.key != currentKey() || s.month != month) return null
        snapshot = null
        return s
    }

    /** 等待本批预取全部完成（不抛异常，单个请求失败也视为完成） */
    suspend fun awaitAll() {
        val s = snapshot ?: return
        s.members.join()
        s.bags.join()
        s.calendar.join()
        s.categories.join()
        s.flows.join()
        s.budgets.join()
    }
}

/** 已完成的预取请求同步取成功值；未完成或失败返回 null */
fun <T> Deferred<Result<T>>.doneValue(): T? =
    if (isCompleted) getCompleted().getOrNull() else null
