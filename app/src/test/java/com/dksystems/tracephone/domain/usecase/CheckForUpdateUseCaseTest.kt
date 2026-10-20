package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.FakeInstalledAppVersion
import com.dksystems.tracephone.domain.FakeLatestReleaseSource
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.model.UpdateCheckFailure
import com.dksystems.tracephone.domain.update.UpdateManifestFormatException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val TRUSTED_URL = "https://github.com/dksystems/TracePhone/releases/latest"

private fun releaseOf(versionCode: Int, downloadUrl: String = TRUSTED_URL) = AppRelease(
    versionCode = versionCode,
    versionName = "0.$versionCode.0",
    downloadUrl = downloadUrl
)

class CheckForUpdateUseCaseTest {

    @Test
    fun `확인 주소가 없으면 지원하지 않는다`() = runTest {
        val source = FakeLatestReleaseSource(isConfigured = false)
        val useCase = CheckForUpdateUseCase(source, FakeInstalledAppVersion(versionCode = 1))

        assertFalse(useCase.isSupported)
        assertEquals(UpdateCheck.Unavailable, useCase())
        // 지원하지 않는 빌드에서는 통신을 시도조차 하지 않는다.
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun `기록된 버전이 더 높으면 새 버전이 있다`() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(release = releaseOf(versionCode = 2)),
            FakeInstalledAppVersion(versionCode = 1)
        )

        val result = useCase()

        assertTrue(result is UpdateCheck.Available)
        assertEquals(2, (result as UpdateCheck.Available).release.versionCode)
    }

    @Test
    fun `버전이 같으면 최신이다`() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(release = releaseOf(versionCode = 5)),
            FakeInstalledAppVersion(versionCode = 5, versionName = "0.5.0")
        )

        assertEquals(UpdateCheck.UpToDate("0.5.0"), useCase())
    }

    @Test
    fun `직접 빌드해 더 높은 버전을 쓰고 있으면 내려가라고 하지 않는다`() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(release = releaseOf(versionCode = 2)),
            FakeInstalledAppVersion(versionCode = 9, versionName = "0.9.0")
        )

        assertEquals(UpdateCheck.UpToDate("0.9.0"), useCase())
    }

    @Test
    fun `연결하지 못하면 네트워크 실패다`() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(error = IOException("연결 실패")),
            FakeInstalledAppVersion(versionCode = 1)
        )

        assertEquals(UpdateCheck.Failed(UpdateCheckFailure.NETWORK), useCase())
    }

    @Test
    fun `형식이 어긋나면 형식 실패다`() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(error = UpdateManifestFormatException("versionCode 없음")),
            FakeInstalledAppVersion(versionCode = 1)
        )

        assertEquals(UpdateCheck.Failed(UpdateCheckFailure.MALFORMED), useCase())
    }

    @Test
    fun `내려받기 주소가 github이 아니면 열지 않는다`() = runTest {
        // 버전 기록은 공개 읽기 저장소에 있다. 쓰기 권한이 새면 주소가 바꿔치기된다.
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(
                release = releaseOf(versionCode = 2, downloadUrl = "https://evil.example/app.apk")
            ),
            FakeInstalledAppVersion(versionCode = 1)
        )

        assertEquals(UpdateCheck.Failed(UpdateCheckFailure.UNTRUSTED_DOWNLOAD_URL), useCase())
    }

    @Test
    fun `github으로 시작하는 다른 호스트도 막는다`() {
        // 접두사만 보면 github.com.evil.example 이 통과한다. 뒤가 끝이거나 슬래시여야 한다.
        assertFalse(isTrustedDownloadUrl("https://github.com.evil.example/app.apk"))
        assertFalse(isTrustedDownloadUrl("https://github.community/app.apk"))
        assertTrue(isTrustedDownloadUrl("https://github.com/a/b/releases"))
        assertTrue(isTrustedDownloadUrl("https://github.com"))
    }

    @Test
    fun `http로 적힌 주소는 막는다`() {
        assertFalse(isTrustedDownloadUrl("http://github.com/a/b"))
    }

    @Test
    fun `userinfo로 호스트를 가린 주소를 막는다`() {
        // https://github.com@evil.example/x 는 실제로 evil.example 로 간다.
        assertFalse(isTrustedDownloadUrl("https://github.com@evil.example/app.apk"))
        assertFalse(isTrustedDownloadUrl("https://github.com:pass@evil.example/app.apk"))
    }

    @Test
    fun `대소문자가 섞여도 받아들인다`() {
        // 스킴과 호스트는 대소문자를 구분하지 않는다. 콘솔에 Https로 적는 일이 있다.
        assertTrue(isTrustedDownloadUrl("HTTPS://GITHUB.COM/a/b"))
        assertTrue(isTrustedDownloadUrl("Https://GitHub.com/a/b"))
    }

    @Test
    fun `대소문자를 바꿔도 다른 호스트는 여전히 막는다`() {
        assertFalse(isTrustedDownloadUrl("HTTPS://GITHUB.COM.EVIL.EXAMPLE/a"))
    }

    @Test
    fun `최신인 경우에는 주소를 검사하지 않는다`() = runTest {
        // 내려받을 일이 없으므로 주소가 어떻든 "최신"이라고 말하는 것이 맞다.
        val useCase = CheckForUpdateUseCase(
            FakeLatestReleaseSource(
                release = releaseOf(versionCode = 1, downloadUrl = "https://evil.example")
            ),
            FakeInstalledAppVersion(versionCode = 1, versionName = "0.1.0")
        )

        assertEquals(UpdateCheck.UpToDate("0.1.0"), useCase())
    }
}
