package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.alert.UsageAlertNotifier
import com.dksystems.tracephone.domain.model.UsageAlertSettings
import com.dksystems.tracephone.domain.preferences.UsageAlertPreferences
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 오늘 한도를 넘긴 앱을 찾아 알린다.
 *
 * **한도를 넘길 때마다 다시 알린다.** 한도가 50MB면 50MB에서 한 번, 100MB에서 또 한 번,
 * 150MB에서 또 한 번이다. 하루 한 번만 알리면 첫 알림 뒤로는 얼마나 더 썼는지 알 길이 없다.
 * 앱마다 마지막으로 알린 배수를 기억해 그 값이 커졌을 때만 알린다.
 *
 * 한 번에 여러 배수를 뛰어넘어도 알림은 한 번이다. 40MB에서 210MB로 뛰었다고
 * 알림 네 개를 쏟아 낼 이유는 없다.
 *
 * 저장소가 아니라 **시스템에서 오늘치를 직접 읽는다.** 판정에는 가장 최신 값이 필요한데
 * 저장소는 6시간 주기로만 갱신되기 때문이다.
 * 같은 이유로 여기서는 저장소에 쓰지 않는다. 15분마다 수백 행을 쓸 이유가 없다.
 *
 * 여기서 로그를 남기지 않는다. domain은 Android SDK에 기대지 않아야 순수 단위 테스트가 가능하다.
 * 결과 기록은 호출부인 [com.dksystems.tracephone.work.UsageAlertWorker]가 맡는다.
 *
 * @return 이번에 알림을 보낸 앱의 수
 */
class EvaluateUsageAlertsUseCase @Inject constructor(
    private val systemUsageReader: SystemUsageReader,
    private val alertPreferences: UsageAlertPreferences,
    private val notifier: UsageAlertNotifier,
    private val clock: Clock
) {

    suspend operator fun invoke(): Int {
        val settings = alertPreferences.settings.first()
        if (!settings.isEnabled) {
            return NOTHING_NOTIFIED
        }

        val today = LocalDate.now(clock)
        val alreadyNotified = alertPreferences.notifiedMultiplesOn(today)

        val reachedMultiples = systemUsageReader.readTodayUsage()
            .associateWith { usage -> settings.reachedMultipleBy(usage.mobile.totalBytes) }
            .filter { (usage, multiple) ->
                multiple > (alreadyNotified[usage.app.uid] ?: UsageAlertSettings.NOT_REACHED)
            }

        if (reachedMultiples.isEmpty()) {
            return NOTHING_NOTIFIED
        }

        reachedMultiples.keys.forEach { usage ->
            notifier.notifyDailyLimitExceeded(
                app = usage.app,
                usedMobileBytes = usage.mobile.totalBytes,
                limitBytes = settings.effectiveLimitBytes
            )
        }

        alertPreferences.markNotified(
            date = today,
            notifiedMultiples = reachedMultiples.mapKeys { (usage, _) -> usage.app.uid }
        )

        return reachedMultiples.size
    }

    private companion object {
        const val NOTHING_NOTIFIED = 0
    }
}
