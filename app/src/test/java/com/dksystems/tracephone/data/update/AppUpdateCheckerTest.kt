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
import org.junit.Assert.assertNull
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
     * 뒤로가기로 앱을 닫으면 부른다. 그렇게 닫았다가 다시 여는 것은 사용자에게 '새로 켜는
     * 일'이므로, 확인이 간격에 막혀 조용히 건너뛰어지면 "열었는데 아무 일도 없다"가 된다.
     */
    @Test
    fun `확인한 기억을 지우면 간격과 무관하게 다시 묻는다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val checker = checkerWith(source, MutableClock(START))

        checker.checkIfStale()
        checker.forgetLastCheck()
        checker.checkIfStale()

        assertEquals(2, source.fetchCount)
    }

    /**
     * 실패를 성공과 같이 10분 붙들면, 네트워크가 1분 뒤 돌아와도 화면에 실패가 남아
     * 사용자가 고장으로 읽는다. 실측으로 겪은 뒤 간격을 갈랐다.
     */
    @Test
    fun `실패한 뒤에는 더 짧은 간격으로 다시 묻는다`() = runTest {
        val source = FakeLatestReleaseSource(error = java.io.IOException("연결 실패"))
        val clock = MutableClock(START)
        val checker = checkerWith(source, clock)

        checker.checkIfStale()
        clock.advance(Duration.ofMinutes(2))
        checker.checkIfStale()

        // 성공했을 때의 간격(10분)이라면 아직 묻지 않았을 시점이다.
        assertEquals(2, source.fetchCount)
    }

    @Test
    fun `실패한 뒤에도 너무 이르면 묻지 않는다`() = runTest {
        val source = FakeLatestReleaseSource(error = java.io.IOException("연결 실패"))
        val clock = MutableClock(START)
        val checker = checkerWith(source, clock)

        checker.checkIfStale()
        clock.advance(Duration.ofSeconds(30))
        checker.checkIfStale()

        assertEquals(1, source.fetchCount)
    }

    /**
     * **0.4.4에서 고친 것과 같은 함정이 닫기에도 있었다.**
     *
     * 닫은 기억이 프로세스 수명 동안 남으면, 안드로이드가 프로세스를 잘 죽이지 않으므로
     * 닫기 한 번이 배너를 영구히 숨긴다. '앱을 다시 켜면 다시 알린다'가 거짓이 된다.
     */
    @Test
    fun `닫은 뒤 다시 확인하면 다시 알린다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val clock = MutableClock(START)
        val checker = checkerWith(source, clock)

        checker.checkIfStale()
        checker.dismiss(newerRelease.versionCode)
        clock.advance(Duration.ofMinutes(11))
        checker.checkIfStale()

        assertNull(checker.dismissedVersionCode.value)
    }

    /** 닫은 직후에 다시 띄우면 닫은 뜻이 없어진다. 다음 확인까지는 닫혀 있어야 한다. */
    @Test
    fun `닫은 직후에는 닫힌 채로 둔다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val checker = checkerWith(source, MutableClock(START))

        checker.checkIfStale()
        checker.dismiss(newerRelease.versionCode)
        checker.checkIfStale()

        assertEquals(newerRelease.versionCode, checker.dismissedVersionCode.value)
    }

    /** 사용자가 직접 '다시 확인'을 눌렀다면 닫아 둔 것도 함께 거둔다. */
    @Test
    fun `다시 확인은 닫은 기억도 지운다`() = runTest {
        val source = FakeLatestReleaseSource(release = newerRelease)
        val checker = checkerWith(source, MutableClock(START))

        checker.checkIfStale()
        checker.dismiss(newerRelease.versionCode)
        checker.recheck()

        assertNull(checker.dismissedVersionCode.value)
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
