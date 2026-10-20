package com.dksystems.tracephone.data.repository

import com.dksystems.tracephone.domain.model.AppUsage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * 오늘치 시스템 조회 결과를 아주 짧은 동안만 기억한다.
 *
 * **왜 저장소 안쪽에 두는가.** [com.dksystems.tracephone.work.UsageAlertWorker] 한 번에
 * 경고 판정 · 한도 판정 · 위젯 갱신이 잇달아 돌고 셋 다 같은 "오늘치"를 본다.
 * 그런데 위젯은 Glance 컴포지션 안에서 스스로 값을 읽으므로 바깥에서 읽은 값을 건네줄 방법이 없다.
 * 세 경로가 반드시 지나는 유일한 지점이 여기다.
 *
 * 낡은 값이 화면에 남지 않도록 두 가지로 막는다.
 * - 날짜가 바뀌면 남은 시간과 무관하게 버린다.
 * - [ttlMillis]를 넘기면 버린다.
 *
 * **빈 목록은 기억하지 않는다.** 빈 목록은 값이 아니라 "권한 없음 또는 조회 실패"의 신호다.
 * 이를 기억하면 권한을 막 허용한 사용자가 [ttlMillis] 동안 계속 0을 보게 된다.
 * 기억하지 않아도 비용이 늘지 않는다. 권한이 없으면 시스템을 두드리기 전에 빠져나오기 때문이다.
 */
class TodayUsageMemo(private val ttlMillis: Long) {

    /** 워커와 위젯 컴포지션이 겹쳐 들어와도 시스템 조회가 두 번 나가지 않게 한다. */
    private val mutex = Mutex()

    private var entry: Entry? = null

    /**
     * 기억해 둔 값이 아직 쓸 만하면 그대로 주고, 아니면 [load]로 새로 읽는다.
     *
     * @param today 조회 대상 날짜. 이 값이 바뀌면 기억한 값을 버린다.
     * @param nowMillis 현재 시각. 호출부가 넘겨 [java.time.Clock]을 바꿔 끼운 테스트가 가능하다.
     */
    suspend fun getOrLoad(
        today: LocalDate,
        nowMillis: Long,
        load: suspend () -> List<AppUsage>
    ): List<AppUsage> = mutex.withLock {
        val remembered = entry?.takeIf { it.isFresh(today, nowMillis, ttlMillis) }
        if (remembered != null) {
            return@withLock remembered.appUsages
        }

        val loaded = load()
        if (loaded.isNotEmpty()) {
            entry = Entry(date = today, loadedAtMillis = nowMillis, appUsages = loaded)
        }
        loaded
    }

    private class Entry(
        val date: LocalDate,
        val loadedAtMillis: Long,
        val appUsages: List<AppUsage>
    ) {

        fun isFresh(today: LocalDate, nowMillis: Long, ttlMillis: Long): Boolean =
            date == today && nowMillis - loadedAtMillis < ttlMillis
    }
}
