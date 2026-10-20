package com.dksystems.tracephone

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.work.UsageSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 앱 진입점.
 *
 * WorkManager의 자동 초기화를 매니페스트에서 끄고 여기서 직접 설정한다.
 * Hilt가 만든 [HiltWorkerFactory]를 넘겨야 Worker에 의존성을 주입할 수 있기 때문이다.
 */
@HiltAndroidApp
class TracePhoneApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var usageSyncScheduler: UsageSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 권한이 아직 없어도 등록해 둔다. 작업 자체가 권한 여부를 확인하고 조용히 건너뛴다.
        // 사용자가 나중에 권한을 켜면 다음 주기부터 자동으로 수집이 시작된다.
        usageSyncScheduler.ensurePeriodicSync()
        usageSyncScheduler.ensureAlertCheck()

        AppLog.info("TracePhone started")
    }
}
