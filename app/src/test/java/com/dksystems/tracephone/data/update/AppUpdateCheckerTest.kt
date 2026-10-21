package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.domain.FakeInstalledAppVersion
import com.dksystems.tracephone.domain.FakeLatestReleaseSource
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.usecase.CheckForUpdateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * '언제 다시 묻는가'를 고정한다.
 *
 * **이 판단이 뒤집히면 조용히 실패한다.** 너무 안 물으면 새 버전이 나온 것을 영원히 모르고,
 * 너무 자주 물으면 사용량을 재는 앱이 자기 통신을 늘린다. 어느 쪽도 화면에 드러나지 않아
 * 눈으로는 알아챌 수 없다.
 *
 * 앞서 겪은 일이 이것이다 — '한 번도 확인하지 않았는가'로 판단했더니 결과가 프로세스 수명
 * 동안 굳어, 기록을 바꿔도 앱이 영원히 같은 답을 보였다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateCheckerTest {

    private val newerRelease = AppRelease(
        versionCode = 7,
        versionName = "0.4.3",
        downloadUrl = "https://github.com/owner/repo/releases/download/v0.4.3/app.apk"
    )

    @Test
    fun `처음 불리면 묻는다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)

        checkerWith(source, MutableClock(START)).checkIfStale()

        assertEquals(1, source.fetchCount)
    }

    @Test
    fun `곧바로 다시 부르면 묻지 않는다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val checker = checkerWith(source, MutableClock(START))

        checker.checkIfStale()
        checker.checkIfStale()

        assertEquals(1, source.fetchCount)
    }

    @Test
    fun `간격이 지나면 다시 묻는다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val clock = MutableClock(START)
        val checker = checkerWith(source, clock)

        checker.checkIfStale()
        clock.advance(Duration.ofMinutes(11))
        checker.checkIfStale()

        assertEquals(2, source.fetchCount)
    }

    /** 기기 시간을 되돌리면 지난 시간이 음수가 된다. 그것을 '방금'으로 읽으면 안 된다. */
    @Test
    fun `기기 시간이 되돌아가면 다시 묻는다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val clock = MutableClock(START)
        val checker = checkerWith(source, clock)

        checker.checkIfStale()
        clock.advance(Duration.ofDays(-1))
        checker.checkIfStale()

        assertEquals(2, source.fetchCount)
    }

    /** 사용자가 직접 누른 것이므로 기다리게 할 이유가 없다. 급할 때의 탈출구다. */
    @Test
    fun `다시 확인은 간격과 무관하게 묻는다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val checker = checkerWith(source, MutableClock(START))

        checker.checkIfStale()
        checker.recheck()

        assertEquals(2, source.fetchCount)
    }

    /**
     * 결과가 아니라 **시도**를 기록하기 때문이다.
     * 실패할 때마다 다시 두드리면 네트워크가 죽은 동안 요청만 쌓인다.
     */
    @Test
    fun `실패해도 간격 안에는 다시 묻지 않는다`() = runTest {
        val source = FakeLatestReleaseSource(error = java.io.IOException("연결 실패"))
        val checker = checkerWith(source, MutableClock(START))

        checker.checkIfStale()
        checker.checkIfStale()

        assertEquals(1, source.fetchCount)
    }

    /**
     * 확인이 `launch`로 돌아가므로 **즉시 실행되는** 디스패처를 쓴다.
     * 미루는 디스패처를 쓰면 `fetchCount`를 세는 시점에 아직 아무것도 일어나지 않는다.
     */
    private fun checkerWith(
        source: FakeLatestReleaseSource,
        clock: Clock
    ): AppUpdateChecker = AppUpdateChecker(
        checkForUpdate = CheckForUpdateUseCase(
            latestReleaseSource = source,
            installedAppVersion = FakeInstalledAppVersion(versionCode = 6)
        ),
        clock = clock,
        applicationScope = CoroutineScope(UnconfinedTestDispatcher())
    )

    private companion object {
        val START: Instant = Instant.parse("2026-10-21T00:00:00Z")
    }
}

/** 테스트가 시간을 앞으로도 뒤로도 움직일 수 있는 시계. */
private class MutableClock(private var current: Instant) : Clock() {

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
