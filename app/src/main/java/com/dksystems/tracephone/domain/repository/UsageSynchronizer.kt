package com.dksystems.tracephone.domain.repository

import com.dksystems.tracephone.domain.model.UsageSyncResult

/**
 * 시스템에서 읽어 저장소로 옮긴다. 이 앱에서 **유일하게 쓰기가 일어나는 방향**이다.
 *
 * [UsageRepository]와 [SystemUsageReader]가 각각 한쪽만 보는 것과 달리 여기는 둘 다 만진다.
 * 그래서 따로 두었다 — 읽기만 하는 곳에 쓰기가 섞여 있으면
 * "이 함수를 불러도 안전한가"를 매번 확인해야 한다.
 *
 * 시스템은 상세 기록을 오래 보관하지 않으므로 여기서 옮겨 두지 않으면 그대로 사라진다.
 * **저장소의 누적 기록이 시스템이 지운 뒤에도 남는 유일한 자산**인 이유다.
 */
interface UsageSynchronizer {

    /**
     * 오늘을 포함한 최근 [dayCount]일을 시스템에서 다시 읽어 저장소에 덮어쓴다.
     *
     * 시스템이 주는 값은 그 날의 누적치이므로 항상 덮어쓰기(upsert)여야 한다.
     * 더하면 동기화 횟수만큼 수치가 부풀려진다.
     *
     * 하루를 읽지 못하면 **그 날만 건너뛰고 계속한다.** 하루 때문에 전체를 포기하면
     * 다음에도 같은 자리에서 멈춘다.
     *
     * @param dayCount 1 이상
     */
    suspend fun syncRecentDays(dayCount: Long): UsageSyncResult
}
