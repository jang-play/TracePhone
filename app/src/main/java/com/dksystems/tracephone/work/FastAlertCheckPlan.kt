package com.dksystems.tracephone.work

import com.dksystems.tracephone.core.SyncConstants

/**
 * 빠른 판정을 어떻게 걸지.
 *
 * @property delayMinutes 지금부터 몇 분 뒤에 판정할지
 * @property restartInterval 이미 걸려 있는 것을 밀어내고 지금부터 다시 셀지
 */
internal data class FastAlertCheckPlan(
    val delayMinutes: Long,
    val restartInterval: Boolean
)

/**
 * 빠른 판정을 걸지 말지, 건다면 어떻게 걸지 정한다.
 *
 * **`work` 패키지에서 조용히 실패할 수 있는 유일한 판단이라 따로 떼어 놨다.**
 * 다른 곳이 깨지면 화면이 이상해져 바로 눈에 띄지만, 여기가 뒤집히면 아무 일도 일어나지 않는다.
 * "알림이 안 온다"는 사실을 며칠 뒤에야 알아챈다. 그래서 WorkManager와 `Context`에서 떼어
 * JVM 테스트로 분기를 전부 고정한다.
 *
 * 배터리 최적화 예외가 없으면 **아무 계획도 세우지 않는다.** 15분 하한은
 * `PeriodicWorkRequest`에만 걸리므로 일회성 작업을 이어 붙이면 더 촘촘히 돌 수는 있지만,
 * 예외가 없으면 App Standby Bucket의 세션 상한을 금세 태우고 남은 시간을 통째로 막힌다.
 * 노리던 5분은커녕 **15분보다 나빠진다.** 그때는 주기 작업에 맡기는 것이 낫다.
 *
 * @param isIgnoringBatteryOptimizations 배터리 최적화 예외로 등록되어 있는가
 * @param restartInterval 판정을 막 끝낸 뒤라 다음 간격을 처음부터 다시 세야 하는가
 * @return 걸어야 할 계획. **null이면 걸지 않고 이미 걸린 것도 걷어낸다.**
 */
internal fun planFastAlertCheck(
    isIgnoringBatteryOptimizations: Boolean,
    restartInterval: Boolean
): FastAlertCheckPlan? {
    if (!isIgnoringBatteryOptimizations) {
        return null
    }

    return FastAlertCheckPlan(
        delayMinutes = SyncConstants.FAST_ALERT_CHECK_INTERVAL_MINUTES,
        restartInterval = restartInterval
    )
}
