package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.FakeSystemUsageReader
import com.dksystems.tracephone.domain.FakeUsageRepository
import com.dksystems.tracephone.domain.alert.MonthlyLimitNotifier
import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class EvaluateMonthlyLimitUseCaseTest {

    private val gigabyte = 1_000L * MonthlyLimitSettings.BYTES_PER_MEGABYTE
    private val zone: ZoneId = ZoneId.of("UTC")

    private val notifiedThresholds = mutableListOf<MonthlyLimitThreshold>()

    private val notifier = MonthlyLimitNotifier { _, threshold ->
        notifiedThresholds += threshold
    }

    /** 날짜별로 알린 지점을 기억하는 가짜 저장소. 실제 구현과 같은 규칙을 따른다. */
    private class FakePreferences(
        settings: MonthlyLimitSettings
    ) : MonthlyLimitPreferences {

        private val settingsFlow = MutableStateFlow(settings)
        private var notifiedCycleStart: LocalDate? = null
        private val notified = mutableSetOf<MonthlyLimitThreshold>()

        override val settings: Flow<MonthlyLimitSettings> = settingsFlow

        override suspend fun setEnabled(enabled: Boolean) = Unit

        override suspend fun setLimitMegabytes(limitMegabytes: Long) = Unit

        override suspend fun setCycleStartDay(cycleStartDay: Int) = Unit

        override suspend fun notifiedThresholdsIn(
            cycleStart: LocalDate
        ): Set<MonthlyLimitThreshold> =
            if (notifiedCycleStart == cycleStart) notified.toSet() else emptySet()

        override suspend fun markNotified(
            cycleStart: LocalDate,
            threshold: MonthlyLimitThreshold
        ) {
            if (notifiedCycleStart != cycleStart) {
                notifiedCycleStart = cycleStart
                notified.clear()
            }
            notified += threshold
        }
    }

    private fun useCaseWith(
        usedGigabytes: Long,
        isEnabled: Boolean = true,
        limitGigabytes: Long = 10L,
        today: LocalDate = LocalDate.of(2026, 9, 20),
        preferences: FakePreferences = FakePreferences(
            MonthlyLimitSettings(
                isEnabled = isEnabled,
                limitMegabytes = limitGigabytes * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
                cycleStartDay = 15
            )
        )
    ): Pair<EvaluateMonthlyLimitUseCase, FakePreferences> {
        val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

        // 주기에 오늘이 들어 있으므로 오늘치는 시스템에서 온다.
        val getMobileUsage = GetMobileUsageForPeriodUseCase(
            repository = FakeUsageRepository(),
            systemUsageReader = FakeSystemUsageReader.withTodayMobileBytes(
                usedGigabytes * gigabyte
            ),
            clock = clock
        )

        val useCase = EvaluateMonthlyLimitUseCase(
            getMonthlyLimitStatus = GetMonthlyLimitStatusUseCase(preferences, getMobileUsage, clock),
            preferences = preferences,
            notifier = notifier
        )
        return useCase to preferences
    }

    @Test
    fun `꺼져 있으면 아무것도 알리지 않는다`() = runTest {
        val (useCase, _) = useCaseWith(usedGigabytes = 10L, isEnabled = false)

        assertNull(useCase())
        assertTrue(notifiedThresholds.isEmpty())
    }

    @Test
    fun `한도를 넣지 않았으면 아무것도 알리지 않는다`() = runTest {
        val (useCase, _) = useCaseWith(usedGigabytes = 10L, limitGigabytes = 0L)

        assertNull(useCase())
        assertTrue(notifiedThresholds.isEmpty())
    }

    @Test
    fun `80퍼센트를 넘기면 경고를 알린다`() = runTest {
        val (useCase, _) = useCaseWith(usedGigabytes = 8L)

        assertEquals(MonthlyLimitThreshold.WARNING, useCase())
        assertEquals(listOf(MonthlyLimitThreshold.WARNING), notifiedThresholds)
    }

    @Test
    fun `같은 지점은 주기당 한 번만 알린다`() = runTest {
        val (useCase, _) = useCaseWith(usedGigabytes = 8L)

        assertEquals(MonthlyLimitThreshold.WARNING, useCase())
        assertNull(useCase())
        assertEquals(1, notifiedThresholds.size)
    }

    /**
     * 80%를 알린 뒤 한도까지 다 쓰면 한 번 더 알려야 한다.
     * 이미 알렸다는 이유로 넘어가면 정작 중요한 순간을 놓친다.
     */
    @Test
    fun `경고를 알린 뒤 한도에 닿으면 다시 알린다`() = runTest {
        val preferences = FakePreferences(
            MonthlyLimitSettings(
                isEnabled = true,
                limitMegabytes = 10L * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
                cycleStartDay = 15
            )
        )
        val (warningUseCase, _) = useCaseWith(usedGigabytes = 8L, preferences = preferences)
        assertEquals(MonthlyLimitThreshold.WARNING, warningUseCase())

        val (reachedUseCase, _) = useCaseWith(usedGigabytes = 10L, preferences = preferences)
        assertEquals(MonthlyLimitThreshold.REACHED, reachedUseCase())

        assertEquals(
            listOf(MonthlyLimitThreshold.WARNING, MonthlyLimitThreshold.REACHED),
            notifiedThresholds
        )
    }

    @Test
    fun `주기가 바뀌면 같은 지점을 다시 알린다`() = runTest {
        val preferences = FakePreferences(
            MonthlyLimitSettings(
                isEnabled = true,
                limitMegabytes = 10L * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
                cycleStartDay = 15
            )
        )
        val (thisCycle, _) = useCaseWith(
            usedGigabytes = 10L,
            today = LocalDate.of(2026, 9, 20),
            preferences = preferences
        )
        assertEquals(MonthlyLimitThreshold.REACHED, thisCycle())

        // 다음 주기(10월 15일 시작)로 넘어가면 다시 알릴 수 있어야 한다.
        val (nextCycle, _) = useCaseWith(
            usedGigabytes = 10L,
            today = LocalDate.of(2026, 10, 20),
            preferences = preferences
        )
        assertEquals(MonthlyLimitThreshold.REACHED, nextCycle())
    }
}
