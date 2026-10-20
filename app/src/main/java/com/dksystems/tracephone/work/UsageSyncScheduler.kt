package com.dksystems.tracephone.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.core.SyncConstants
import com.dksystems.tracephone.core.WorkNames
import com.dksystems.tracephone.core.isIgnoringBatteryOptimizations
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 동기화 작업의 등록을 한곳에서 관리한다.
 *
 * WorkManager 호출을 여기로 모아두면 주기나 정책을 바꿀 때 고칠 곳이 하나로 유지된다.
 */
@Singleton
class UsageSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * 주기 동기화를 보장한다. 이미 등록되어 있으면 그대로 둔다.
     *
     * 배터리·네트워크 제약을 걸지 않는다. 이 작업은 네트워크를 쓰지 않고
     * 로컬 통계만 읽으므로 제약을 걸면 실행이 미뤄지기만 할 뿐 얻는 것이 없다.
     */
    fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<DailyUsageSyncWorker>(
            SyncConstants.SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WorkNames.PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        AppLog.info("Periodic usage sync ensured: intervalHours=${SyncConstants.SYNC_INTERVAL_HOURS}")
    }

    /**
     * 사용량 경고 판정을 주기적으로 돌린다. 이미 등록되어 있으면 그대로 둔다.
     *
     * 경고 설정이 꺼져 있어도 등록해 둔다. 판정 자체가 설정을 먼저 확인하고 바로 빠져나오므로
     * 비용이 거의 없고, 사용자가 설정을 켠 순간부터 별도 조치 없이 동작한다.
     *
     * 판정은 **두 갈래로 걸린다.** 15분 주기 작업은 시스템이 관리하므로 끊길 일이 없고,
     * 빠른 판정은 스스로를 다시 걸어야 이어진다. 전자가 후자의 안전망이다.
     */
    fun ensureAlertCheck() {
        val request = PeriodicWorkRequestBuilder<UsageAlertWorker>(
            SyncConstants.ALERT_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WorkNames.PERIODIC_ALERT_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        enqueueFastAlertCheck(restartInterval = false)

        AppLog.info(
            "Usage alert check ensured: intervalMinutes=${SyncConstants.ALERT_CHECK_INTERVAL_MINUTES}"
        )
    }

    /**
     * 판정이 한 번 끝날 때마다 부른다. 다음 빠른 판정을 처음부터 다시 센다.
     *
     * 15분 작업과 빠른 판정이 잇달아 돌아도 다음 확인까지의 간격이 항상 같아진다.
     */
    fun rescheduleFastAlertCheck() {
        enqueueFastAlertCheck(restartInterval = true)
    }

    /**
     * [planFastAlertCheck]가 정한 대로 WorkManager에 옮긴다.
     *
     * **여기에는 판단이 없다.** 걸지 말지와 얼마 뒤에 걸지는 전부 저쪽에서 정하고,
     * 이 함수는 그 결과를 WorkManager의 말로 바꾸기만 한다.
     * 그래야 판단 쪽을 JVM 테스트로 고정할 수 있다.
     */
    private fun enqueueFastAlertCheck(restartInterval: Boolean) {
        val plan = planFastAlertCheck(
            isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations(),
            restartInterval = restartInterval
        )

        if (plan == null) {
            workManager.cancelUniqueWork(WorkNames.FAST_ALERT_CHECK)
            return
        }

        val request = OneTimeWorkRequestBuilder<UsageAlertWorker>()
            .setInitialDelay(plan.delayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            WorkNames.FAST_ALERT_CHECK,
            plan.existingWorkPolicy,
            request
        )
    }

    /** 간격을 다시 세라는 뜻이 WorkManager에서는 기존 작업을 갈아 끼우는 것이다. */
    private val FastAlertCheckPlan.existingWorkPolicy: ExistingWorkPolicy
        get() = if (restartInterval) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

    /**
     * 즉시 한 번 동기화한다. 화면 진입 시와 권한을 막 허용한 직후에 사용한다.
     *
     * @param forceInitialBackfill true면 최대 [SyncConstants.INITIAL_BACKFILL_DAYS]일치를 다시 수집한다.
     */
    fun requestImmediateSync(forceInitialBackfill: Boolean = false) {
        val inputData = Data.Builder()
            .putBoolean(DailyUsageSyncWorker.KEY_FORCE_INITIAL_BACKFILL, forceInitialBackfill)
            .build()

        val request = OneTimeWorkRequestBuilder<DailyUsageSyncWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        // REPLACE를 쓰면 화면을 빠르게 오갈 때 직전 작업이 취소되고 다시 시작해 아무것도 끝나지 않는다.
        // 이미 대기 중인 작업이 있으면 그것이 끝나기를 기다린다.
        workManager.enqueueUniqueWork(
            WorkNames.ONE_TIME_SYNC,
            ExistingWorkPolicy.KEEP,
            request
        )

        AppLog.debug("Immediate usage sync requested: forceInitialBackfill=$forceInitialBackfill")
    }
}
