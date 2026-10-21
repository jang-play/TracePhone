package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.core.SyncConstants
import com.dksystems.tracephone.domain.model.UsageSyncResult
import com.dksystems.tracephone.domain.preferences.SyncStatusPreferences
import com.dksystems.tracephone.domain.repository.UsageRepository
import com.dksystems.tracephone.domain.repository.UsageSynchronizer
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * 시스템 통계를 저장소로 옮긴다.
 *
 * 최초 실행이면 긴 백필을, 그 뒤로는 **마지막 확인 이후 지난 만큼** 다시 훑는다.
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
            daysToRecheck()
        }

        val result = synchronizer.syncRecentDays(dayCount)
        recordIfChecked(result)
        return result
    }

    /**
     * 평소 동기화에서 거슬러 올라갈 날수. **멈춰 있던 기간만큼 늘어난다.**
     *
     * ### 고정 일수로는 기록을 잃는다
     * 저장된 기록이 하나라도 있으면 긴 백필 조건이 다시 성립하지 않으므로, 고정 일수를 쓰면
     * **그보다 오래 멈춰 있던 기간을 아무도 읽지 않는다.** 앱을 강제 종료해 두거나, 폰을
     * 보름 꺼 두거나, Doze가 워커를 미루거나, 오래된 백업을 새 기기에 복원하면 그렇게 된다.
     * 시스템은 그 기록을 머지않아 지우고, 그러면 복구할 방법이 없다.
     *
     * 지난 날수에 1을 더하는 것은 **확인한 그 날 자체도 다시 읽어야** 하기 때문이다.
     * 마지막 확인은 그 날의 한가운데에 있었고, 그 뒤에 쓴 양은 아직 저장되지 않았다.
     *
     * ### 확인 기록이 없으면 길게 훑는다
     * 저장된 기록은 있는데 확인 시각이 없다면 공백의 크기를 알 수 없다. 모르는 채 짧게
     * 훑으면 조용히 잃는 쪽이고, 길게 훑으면 시스템이 보관하지 않는 날짜를 몇 번 헛도는
     * 정도가 전부다. 어느 쪽이 더 나쁜지가 분명하다.
     *
     * [SyncConstants.RECENT_BACKFILL_DAYS]를 하한으로 두어, 방금 확인했더라도 최근 며칠은
     * 늘 다시 훑는다. [SyncConstants.INITIAL_BACKFILL_DAYS]를 상한으로 두는 이유는
     * 그보다 과거는 시스템이 어차피 돌려주지 않기 때문이다.
     */
    private suspend fun daysToRecheck(): Long {
        val lastSuccessAtMillis = syncStatusPreferences.lastSuccessfulSyncAtMillis.first()
            ?: return SyncConstants.INITIAL_BACKFILL_DAYS

        val elapsedDays = Duration.ofMillis(clock.millis() - lastSuccessAtMillis).toDays()

        // 기기 시간을 되돌리면 음수가 된다. 하한이 그것까지 함께 걸러낸다.
        return (elapsedDays + 1).coerceIn(
            SyncConstants.RECENT_BACKFILL_DAYS,
            SyncConstants.INITIAL_BACKFILL_DAYS
        )
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
