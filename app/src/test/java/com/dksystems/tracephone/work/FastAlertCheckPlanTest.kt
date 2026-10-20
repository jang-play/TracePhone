package com.dksystems.tracephone.work

import com.dksystems.tracephone.core.SyncConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 빠른 판정을 걸지 말지 정하는 분기를 고정한다.
 *
 * **이 앱에서 조용히 실패할 수 있는 유일한 판단이다.** 화면이 깨지면 바로 보이지만
 * 여기가 뒤집히면 아무 일도 일어나지 않고, 며칠 뒤 "알림이 안 왔다"로만 드러난다.
 * 그래서 분기를 하나도 빼지 않고 적어 둔다.
 */
class FastAlertCheckPlanTest {

    @Test
    fun `배터리 최적화 예외가 없으면 아무 계획도 세우지 않는다`() {
        // null은 "걸지 말고 이미 걸린 것도 걷어내라"는 뜻이다.
        // 예외 없이 5분 주기를 노리면 세션 상한을 태우고 15분보다 나빠진다.
        val plan = planFastAlertCheck(
            isIgnoringBatteryOptimizations = false,
            restartInterval = false
        )

        assertNull(plan)
    }

    @Test
    fun `예외가 없으면 간격을 다시 세라고 해도 걸지 않는다`() {
        // 판정을 막 끝낸 뒤라도 예외가 풀렸으면 물러나야 한다.
        // 사용자가 시스템 설정에서 예외를 끄면 이 경로로 스스로 걷힌다.
        val plan = planFastAlertCheck(
            isIgnoringBatteryOptimizations = false,
            restartInterval = true
        )

        assertNull(plan)
    }

    @Test
    fun `예외가 있으면 정해진 간격으로 건다`() {
        val plan = planFastAlertCheck(
            isIgnoringBatteryOptimizations = true,
            restartInterval = false
        )

        // 숫자를 직접 적지 않는다. 상수를 바꿨는데 화면만 따라오지 않는 일을 막는다.
        assertEquals(SyncConstants.FAST_ALERT_CHECK_INTERVAL_MINUTES, plan?.delayMinutes)
    }

    @Test
    fun `앱이 뜰 때는 이미 걸린 간격을 건드리지 않는다`() {
        // 앱을 열 때마다 간격을 다시 세면, 자주 여는 사람은 판정이 영영 오지 않는다.
        val plan = planFastAlertCheck(
            isIgnoringBatteryOptimizations = true,
            restartInterval = false
        )

        assertEquals(false, plan?.restartInterval)
    }

    @Test
    fun `판정을 끝낸 뒤에는 간격을 처음부터 다시 센다`() {
        // 15분 안전망과 빠른 판정이 잇달아 돌아도 다음 확인까지의 간격이 늘 같아진다.
        val plan = planFastAlertCheck(
            isIgnoringBatteryOptimizations = true,
            restartInterval = true
        )

        assertEquals(true, plan?.restartInterval)
    }

    @Test
    fun `빠른 간격은 주기 작업의 하한보다 짧다`() {
        // 이 부등식이 깨지면 빠른 판정을 둘 이유 자체가 없어진다.
        // 15분 하한은 PeriodicWorkRequest에만 걸리므로 일회성 작업만 이보다 짧게 갈 수 있다.
        assertTrue(
            SyncConstants.FAST_ALERT_CHECK_INTERVAL_MINUTES <
                SyncConstants.ALERT_CHECK_INTERVAL_MINUTES
        )
    }
}
