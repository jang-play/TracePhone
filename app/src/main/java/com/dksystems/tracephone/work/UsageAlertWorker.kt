package com.dksystems.tracephone.work

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.domain.usecase.EvaluateMonthlyLimitUseCase
import com.dksystems.tracephone.domain.usecase.EvaluateUsageAlertsUseCase
import com.dksystems.tracephone.widget.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 앱이 실행 중이 아니어도 사용량 경고와 요금제 한도를 판정한다.
 *
 * WorkManager가 작업을 시스템에 등록해 두므로 앱 프로세스가 죽어 있어도 깨워서 실행하고,
 * 재부팅 후에도 스스로 다시 등록한다.
 *
 * 두 판정을 한 작업에 모은 이유는 둘 다 오늘치 조회 하나로 끝나기 때문이다.
 * 작업을 나누면 같은 조회를 두 번 하게 되고 깨어나는 횟수만 늘어난다.
 * 뒤이은 위젯 갱신까지 같은 조회를 나눠 쓴다.
 * 셋을 합쳐 주는 곳은 여기가 아니라 저장소다 —
 * 위젯은 Glance 컴포지션 안에서 스스로 값을 읽어 바깥에서 건네줄 수 없기 때문이다.
 * [com.dksystems.tracephone.data.repository.TodayUsageMemo]를 참고한다.
 *
 * 마지막에 **다음 판정을 스스로 다시 건다.** 배터리 최적화 예외일 때 15분보다 촘촘히 도는
 * 유일한 방법이라서다. 자세한 이유는 [UsageSyncScheduler.rescheduleFastAlertCheck]에 있다.
 */
@HiltWorker
class UsageAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val evaluateUsageAlerts: EvaluateUsageAlertsUseCase,
    private val evaluateMonthlyLimit: EvaluateMonthlyLimitUseCase,
    private val widgetRefresher: WidgetRefresher,
    private val syncScheduler: UsageSyncScheduler
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result = try {
        val startedAtMillis = SystemClock.elapsedRealtime()

        val notifiedAppCount = evaluateUsageAlerts()
        val notifiedThreshold = evaluateMonthlyLimit()

        // 주기적으로 도는 이 작업이 위젯을 최신으로 유지하는 주된 경로다.
        widgetRefresher.refresh()

        // 판정을 마친 지금부터 다시 센다. 예외가 풀렸으면 여기서 스스로 물러난다.
        syncScheduler.rescheduleFastAlertCheck()

        // 알린 것이 없어도 남긴다. 이 한 줄이 "워커가 실제로 몇 분마다 깨어나는가"를 답한다.
        // WorkManager의 주기는 요청값일 뿐이고, Doze와 App Standby Bucket이 실행을 미룬다.
        AppLog.info(
            "Usage alert check finished: attempt=$runAttemptCount, " +
                "alertedApps=$notifiedAppCount, " +
                "limitThreshold=${notifiedThreshold?.percent ?: NO_THRESHOLD_REACHED}, " +
                "elapsedMillis=${SystemClock.elapsedRealtime() - startedAtMillis}"
        )

        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unexpected: Exception) {
        AppLog.error(
            "Usage alert evaluation failed; WorkManager 백오프로 재시도합니다.",
            unexpected
        )
        Result.retry()
    }

    private companion object {

        /** 한도 지점에 닿지 않았을 때 로그에 남길 값. 숫자와 구분되어야 읽힌다. */
        const val NO_THRESHOLD_REACHED = "none"
    }
}
