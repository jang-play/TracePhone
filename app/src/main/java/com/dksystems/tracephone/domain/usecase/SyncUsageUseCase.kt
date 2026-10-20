package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.core.SyncConstants
import com.dksystems.tracephone.domain.model.UsageSyncResult
import com.dksystems.tracephone.domain.preferences.SyncStatusPreferences
import com.dksystems.tracephone.domain.repository.UsageRepository
import com.dksystems.tracephone.domain.repository.UsageSynchronizer
import java.time.Clock
import javax.inject.Inject

/**
 * 시스템 통계를 저장소로 옮긴다.
 *
 * 최초 실행이면 긴 백필을, 그 뒤로는 최근 며칠만 다시 훑는다.
 * "얼마나 거슬러 올라갈지"를 판단하는 책임을 한곳에 모아 호출부가 신경 쓰지 않게 한다.
 */
class SyncUsageUseCase @Inject constructor(
    private val repository: UsageRepository,
    private val synchronizer: UsageSynchronizer,
    private val syncStatusPreferences: SyncStatusPreferences,
    private val clock: Clock
) {

    /**
     * @param forceInitialBackfill true면 저장 기록 유무와 무관하게 긴 백필을 수행한다.
     *   권한을 막 허용한 직후처럼 과거를 한 번에 채워야 할 때 사용한다.
     */
    suspend operator fun invoke(forceInitialBackfill: Boolean = false): UsageSyncResult {
        val hasStoredUsage = repository.hasAnyStoredUsage()

        // 포그라운드 구분을 도입하기 전에 쌓인 자료만 있는 경우에도 한 번은 길게 훑는다.
        // 시스템이 아직 보관 중인 기간만큼 지난 기록에 구분값이 채워진다.
        // 이미 구분값이 하나라도 저장되면 다음부터는 이 조건이 성립하지 않아 한 번만 수행된다.
        val needsStateBackfill = hasStoredUsage && !repository.hasAnyStateRecordedUsage()

        val needsInitialBackfill = forceInitialBackfill || !hasStoredUsage || needsStateBackfill

        val dayCount = if (needsInitialBackfill) {
            SyncConstants.INITIAL_BACKFILL_DAYS
        } else {
            SyncConstants.RECENT_BACKFILL_DAYS
        }

        val result = synchronizer.syncRecentDays(dayCount)
        recordIfChecked(result)
        return result
    }

    /**
     * 실제로 하루라도 읽어 냈을 때만 '확인함'으로 남긴다.
     *
     * **[UsageSyncResult.Success]만으로는 부족하다.** 모든 날짜가 조회에 실패해 건너뛰어져도
     * `syncedDayCount = 0`인 성공이 돌아온다. 그것까지 기록하면 아무것도 읽지 못한 동기화가
     * 화면에 "방금 확인함"을 적게 된다.
     *
     * 저장한 레코드 수는 보지 않는다. 데이터를 한 바이트도 쓰지 않은 날에는 저장할 것이
     * 없지만 확인은 분명히 일어났고, 그 상태를 '확인 못 함'으로 적으면 거짓이 된다.
     */
    private suspend fun recordIfChecked(result: UsageSyncResult) {
        if (result is UsageSyncResult.Success && result.syncedDayCount > 0) {
            syncStatusPreferences.recordSuccessfulSync(clock.millis())
        }
    }
}
