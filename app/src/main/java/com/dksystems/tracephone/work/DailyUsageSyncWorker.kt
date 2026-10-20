package com.dksystems.tracephone.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.domain.model.UsageSyncResult
import com.dksystems.tracephone.domain.usecase.SyncUsageUseCase
import com.dksystems.tracephone.widget.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 시스템 통계를 저장소로 옮기는 백그라운드 작업.
 *
 * 이 앱의 핵심 가치는 "시스템이 지운 뒤에도 남는 기록"이므로,
 * 이 작업이 꾸준히 도는 것이 곧 제품의 품질이다.
 */
@HiltWorker
class DailyUsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val syncUsage: SyncUsageUseCase,
    private val widgetRefresher: WidgetRefresher
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val forceInitialBackfill = inputData.getBoolean(KEY_FORCE_INITIAL_BACKFILL, false)

        return try {
            val result = toWorkResult(syncUsage(forceInitialBackfill))

            // 저장된 값이 바뀌었으므로 홈 화면 숫자도 함께 맞춘다.
            widgetRefresher.refresh()

            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: Exception) {
            AppLog.error(
                "Usage sync worker failed unexpectedly; WorkManager 백오프로 재시도합니다.",
                unexpected
            )
            Result.retry()
        }
    }

    /**
     * 권한이 없는 상태는 재시도로 해결되지 않는다.
     *
     * 사용자가 설정에서 직접 켜야 하므로 실패로 남겨 백오프를 태우는 대신
     * 성공으로 마무리하고, 다음 주기나 화면 진입 시 다시 시도하게 둔다.
     */
    private fun toWorkResult(syncResult: UsageSyncResult): Result = when (syncResult) {
        is UsageSyncResult.Success -> Result.success()

        UsageSyncResult.PermissionRequired -> {
            AppLog.info("Usage sync skipped: 사용 정보 접근 권한이 아직 허용되지 않았습니다.")
            Result.success()
        }

        is UsageSyncResult.Failure -> {
            AppLog.error("Usage sync failed; 재시도를 예약합니다.", syncResult.cause)
            Result.retry()
        }
    }

    companion object {

        /** true면 저장 기록 유무와 무관하게 긴 백필을 수행한다. */
        const val KEY_FORCE_INITIAL_BACKFILL = "force_initial_backfill"
    }
}
