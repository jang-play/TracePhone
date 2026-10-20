package com.dksystems.tracephone.data.repository

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageStateSplit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayUsageMemoTest {

    private val ttlMillis = 30_000L
    private val today = LocalDate.of(2026, 9, 7)
    private val tomorrow = today.plusDays(1)
    private val startMillis = 1_000_000L

    /** 실제로 시스템을 몇 번 두드렸는지 센다. 이 프로젝트에는 mock 라이브러리가 없다. */
    private class CountingLoader(private val result: List<AppUsage>) {

        var loadCount = 0
            private set

        suspend fun load(): List<AppUsage> {
            loadCount++
            return result
        }
    }

    @Test
    fun `유효 시간 안에서는 한 번만 읽는다`() = runTest {
        val memo = TodayUsageMemo(ttlMillis)
        val loader = CountingLoader(listOf(usage(uid = 1)))

        repeat(3) { memo.getOrLoad(today, startMillis, loader::load) }

        assertEquals(1, loader.loadCount)
    }

    @Test
    fun `유효 시간을 넘기면 다시 읽는다`() = runTest {
        val memo = TodayUsageMemo(ttlMillis)
        val loader = CountingLoader(listOf(usage(uid = 1)))

        memo.getOrLoad(today, startMillis, loader::load)
        memo.getOrLoad(today, startMillis + ttlMillis, loader::load)

        assertEquals(2, loader.loadCount)
    }

    @Test
    fun `날짜가 바뀌면 시간이 남아 있어도 다시 읽는다`() = runTest {
        val memo = TodayUsageMemo(ttlMillis)
        val loader = CountingLoader(listOf(usage(uid = 1)))

        memo.getOrLoad(today, startMillis, loader::load)
        memo.getOrLoad(tomorrow, startMillis, loader::load)

        assertEquals(2, loader.loadCount)
    }

    @Test
    fun `빈 목록은 기억하지 않는다`() = runTest {
        // 빈 목록은 값이 아니라 "권한 없음 또는 조회 실패"의 신호다.
        // 이를 기억하면 권한을 막 허용한 사용자가 유효 시간 동안 계속 0을 본다.
        val memo = TodayUsageMemo(ttlMillis)
        val loader = CountingLoader(emptyList())

        memo.getOrLoad(today, startMillis, loader::load)
        memo.getOrLoad(today, startMillis, loader::load)

        assertEquals(2, loader.loadCount)
    }

    @Test
    fun `동시에 들어와도 조회는 한 번이다`() = runTest {
        // 워커의 판정과 위젯 컴포지션은 같은 디스패처 위에서 겹칠 수 있다.
        // 단일 실행을 보장하지 않으면 절감 효과가 그대로 사라진다.
        val memo = TodayUsageMemo(ttlMillis)
        val firstLoadStarted = CompletableDeferred<Unit>()
        var loadCount = 0

        val first = async {
            memo.getOrLoad(today, startMillis) {
                loadCount++
                firstLoadStarted.await()
                listOf(usage(uid = 1))
            }
        }
        val second = async {
            memo.getOrLoad(today, startMillis) {
                loadCount++
                listOf(usage(uid = 2))
            }
        }

        // 두 코루틴이 각자 자리를 잡은 뒤에야 첫 조회를 끝낸다.
        testScheduler.runCurrent()
        firstLoadStarted.complete(Unit)

        assertEquals(listOf(usage(uid = 1)), first.await())
        assertEquals(listOf(usage(uid = 1)), second.await())
        assertEquals(1, loadCount)
    }

    private fun usage(uid: Int) = AppUsage(
        app = AppIdentity(uid = uid, packageName = "com.example.app$uid", label = "앱 $uid"),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit.allUnknown(totalBytes = 1_000L),
            wifi = UsageStateSplit.ZERO
        )
    )
}
