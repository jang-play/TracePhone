package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.FakeUsageAlertPreferences
import com.dksystems.tracephone.domain.FakeSystemUsageReader
import com.dksystems.tracephone.domain.alert.UsageAlertNotifier
import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.UsageAlertSettings
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageStateSplit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class EvaluateUsageAlertsUseCaseTest {

    private val limitBytes = 500_000_000L

    /** 2026-09-03 12:00 (한국) 고정. 날짜가 바뀌는 상황을 만들려면 시계를 갈아 끼운다. */
    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = Clock.fixed(Instant.parse("2026-09-03T03:00:00Z"), seoul)
    private val tomorrow = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), seoul)

    /** 알림이 어떤 앱에 갔는지 기록만 하는 가짜 알림기. */
    private class RecordingNotifier : UsageAlertNotifier {
        val notifiedUids = mutableListOf<Int>()

        override fun notifyDailyLimitExceeded(
            app: AppIdentity,
            usedMobileBytes: Long,
            limitBytes: Long
        ) {
            notifiedUids += app.uid
        }
    }

    private fun usage(
        uid: Int,
        mobileBytes: Long = 0L,
        wifiBytes: Long = 0L,
        mobileForegroundBytes: Long = 0L,
        wifiForegroundBytes: Long = 0L
    ) = AppUsage(
        app = AppIdentity(uid = uid, packageName = "com.example.app$uid", label = "앱 $uid"),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit(
                totalBytes = mobileBytes,
                foregroundBytes = mobileForegroundBytes,
                unknownBytes = 0L
            ),
            wifi = UsageStateSplit(
                totalBytes = wifiBytes,
                foregroundBytes = wifiForegroundBytes,
                unknownBytes = 0L
            )
        )
    )

    private fun useCaseWith(
        usages: List<AppUsage>,
        isEnabled: Boolean = true,
        preferences: FakeUsageAlertPreferences = FakeUsageAlertPreferences(
            UsageAlertSettings(
            isEnabled = isEnabled,
            presetLimitBytes = limitBytes,
            customLimitMegabytes = UsageAlertSettings.CUSTOM_LIMIT_DISABLED
        )
        ),
        notifier: RecordingNotifier = RecordingNotifier(),
        clock: Clock = today
    ): Triple<EvaluateUsageAlertsUseCase, RecordingNotifier, FakeUsageAlertPreferences> {
        val useCase = EvaluateUsageAlertsUseCase(
            systemUsageReader = FakeSystemUsageReader(todayUsage = usages),
            alertPreferences = preferences,
            notifier = notifier,
            clock = clock
        )
        return Triple(useCase, notifier, preferences)
    }

    @Test
    fun `경고가 꺼져 있으면 아무것도 알리지 않는다`() = runTest {
        val (useCase, notifier, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = limitBytes * 2)),
            isEnabled = false
        )

        assertEquals(0, useCase())
        assertEquals(emptyList<Int>(), notifier.notifiedUids)
    }

    @Test
    fun `한도를 넘긴 앱만 알린다`() = runTest {
        val (useCase, notifier, _) = useCaseWith(
            usages = listOf(
                usage(uid = 1, mobileBytes = limitBytes + 1),
                usage(uid = 2, mobileBytes = limitBytes - 1)
            )
        )

        assertEquals(1, useCase())
        assertEquals(listOf(1), notifier.notifiedUids)
    }

    @Test
    fun `한도와 정확히 같은 값도 초과로 본다`() = runTest {
        val (useCase, notifier, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = limitBytes))
        )

        useCase()

        assertEquals(listOf(1), notifier.notifiedUids)
    }

    @Test
    fun `Wi-Fi만 많이 쓴 앱은 경고 대상이 아니다`() = runTest {
        val (useCase, notifier, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = 0L, wifiBytes = limitBytes * 10))
        )

        assertEquals(0, useCase())
        assertEquals(emptyList<Int>(), notifier.notifiedUids)
    }

    /** 한도를 다시 넘기기 전까지는 같은 앱을 다시 알리지 않는다. */
    @Test
    fun `같은 구간에서는 다시 알리지 않는다`() = runTest {
        val (useCase, notifier, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = limitBytes * 2))
        )

        useCase()
        useCase()
        useCase()

        assertEquals(listOf(1), notifier.notifiedUids)
    }

    @Test
    fun `날짜가 바뀌면 같은 앱을 다시 알린다`() = runTest {
        val sharedPreferences = FakeUsageAlertPreferences(
            UsageAlertSettings(
            isEnabled = true,
            presetLimitBytes = limitBytes,
            customLimitMegabytes = UsageAlertSettings.CUSTOM_LIMIT_DISABLED
        )
        )
        val sharedNotifier = RecordingNotifier()
        val usages = listOf(usage(uid = 1, mobileBytes = limitBytes * 2))

        val (todayUseCase, _, _) = useCaseWith(usages, preferences = sharedPreferences, notifier = sharedNotifier, clock = today)
        todayUseCase()

        val (tomorrowUseCase, _, _) = useCaseWith(usages, preferences = sharedPreferences, notifier = sharedNotifier, clock = tomorrow)
        tomorrowUseCase()

        assertEquals(listOf(1, 1), sharedNotifier.notifiedUids)
    }

    @Test
    fun `이미 알린 앱이 있어도 새로 넘긴 앱은 알린다`() = runTest {
        val sharedPreferences = FakeUsageAlertPreferences(
            UsageAlertSettings(
            isEnabled = true,
            presetLimitBytes = limitBytes,
            customLimitMegabytes = UsageAlertSettings.CUSTOM_LIMIT_DISABLED
        )
        )
        val sharedNotifier = RecordingNotifier()

        val (firstRun, _, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = limitBytes * 2)),
            preferences = sharedPreferences,
            notifier = sharedNotifier
        )
        firstRun()

        // 앱 1은 배수가 그대로다. 여기서 보려는 것은 '새로 넘긴 앱 2가 알림을 받는가'뿐이다.
        val (secondRun, _, _) = useCaseWith(
            usages = listOf(
                usage(uid = 1, mobileBytes = limitBytes * 2),
                usage(uid = 2, mobileBytes = limitBytes * 2)
            ),
            preferences = sharedPreferences,
            notifier = sharedNotifier
        )
        secondRun()

        assertEquals(listOf(1, 2), sharedNotifier.notifiedUids)
    }

    /**
     * 이 기능의 핵심이다. 한도가 50이면 50, 100, 150에서 각각 한 번씩 알려야 한다.
     * 첫 알림 뒤로 잠잠하면 사용자는 얼마나 더 썼는지 알 길이 없다.
     */
    @Test
    fun `한도를 다시 넘길 때마다 알린다`() = runTest {
        val sharedPreferences = enabledPreferences()
        val sharedNotifier = RecordingNotifier()

        listOf(limitBytes, limitBytes * 2, limitBytes * 3).forEach { usedBytes ->
            val (useCase, _, _) = useCaseWith(
                usages = listOf(usage(uid = 1, mobileBytes = usedBytes)),
                preferences = sharedPreferences,
                notifier = sharedNotifier
            )
            useCase()
        }

        assertEquals(listOf(1, 1, 1), sharedNotifier.notifiedUids)
    }

    /** 같은 구간 안에서 더 썼다고 다시 알리면 안 된다. */
    @Test
    fun `배수가 바뀌지 않으면 더 써도 알리지 않는다`() = runTest {
        val sharedPreferences = enabledPreferences()
        val sharedNotifier = RecordingNotifier()

        listOf(limitBytes, limitBytes + 1, limitBytes * 2 - 1).forEach { usedBytes ->
            val (useCase, _, _) = useCaseWith(
                usages = listOf(usage(uid = 1, mobileBytes = usedBytes)),
                preferences = sharedPreferences,
                notifier = sharedNotifier
            )
            useCase()
        }

        assertEquals(listOf(1), sharedNotifier.notifiedUids)
    }

    /**
     * 15분 사이에 한도의 네 배를 써 버릴 수도 있다.
     * 그렇다고 알림 네 개를 쏟아 낼 이유는 없다.
     */
    @Test
    fun `여러 배수를 한 번에 뛰어넘어도 알림은 한 번이다`() = runTest {
        val sharedPreferences = enabledPreferences()
        val sharedNotifier = RecordingNotifier()

        val (useCase, _, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = limitBytes * 4)),
            preferences = sharedPreferences,
            notifier = sharedNotifier
        )
        useCase()

        assertEquals(listOf(1), sharedNotifier.notifiedUids)

        // 네 배를 이미 알렸으므로 다음 알림은 다섯 배를 넘겨야 나간다.
        val (sameBand, _, _) = useCaseWith(
            usages = listOf(usage(uid = 1, mobileBytes = limitBytes * 5 - 1)),
            preferences = sharedPreferences,
            notifier = sharedNotifier
        )
        sameBand()

        assertEquals(listOf(1), sharedNotifier.notifiedUids)
    }

    private fun enabledPreferences() = FakeUsageAlertPreferences(
        UsageAlertSettings(
            isEnabled = true,
            presetLimitBytes = limitBytes,
            customLimitMegabytes = UsageAlertSettings.CUSTOM_LIMIT_DISABLED
        )
    )
}
