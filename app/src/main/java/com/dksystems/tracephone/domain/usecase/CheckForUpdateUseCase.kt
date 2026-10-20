package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.model.UpdateCheckFailure
import com.dksystems.tracephone.domain.update.InstalledAppVersion
import com.dksystems.tracephone.domain.update.LatestReleaseSource
import com.dksystems.tracephone.domain.update.UpdateManifestFormatException
import java.io.IOException
import javax.inject.Inject

/**
 * 내려받기를 허용하는 주소의 호스트.
 *
 * 최신 버전 기록은 **공개 읽기**로 두는 저장소에서 온다. 쓰기 권한이 새면 누군가
 * 내려받기 주소를 바꿔치기할 수 있다. 앱이 어떤 주소든 열어 주면 그 사고가 곧바로
 * 악성 APK 설치로 이어지므로, 배포가 실제로 놓이는 곳만 허용한다.
 */
private const val TRUSTED_DOWNLOAD_HOST = "github.com"

private const val HTTPS_SCHEME = "https://"

/**
 * 새 버전이 있는지 확인한다.
 *
 * 버전 비교는 [AppRelease.versionCode]만 쓴다. 이름(`0.10.0` 대 `0.9.0`)으로 비교하면
 * 문자열 순서 때문에 뒤집힌다.
 *
 * 기록된 버전이 설치된 것보다 **낮은** 경우도 [UpdateCheck.UpToDate]로 본다.
 * 직접 빌드해 설치한 경우가 그렇고, 그때 "구버전으로 내려가라"고 권할 이유가 없다.
 */
class CheckForUpdateUseCase @Inject constructor(
    private val latestReleaseSource: LatestReleaseSource,
    private val installedAppVersion: InstalledAppVersion
) {

    /**
     * 이 빌드가 업데이트 확인을 지원하는가.
     *
     * 화면이 카드를 내보일지 **묻기 전에** 알아야 한다. 확인을 한 번 돌려 본 뒤에
     * 숨기면 지원하지 않는 빌드에서도 카드가 한 번 번쩍인다.
     */
    val isSupported: Boolean get() = latestReleaseSource.isConfigured

    suspend operator fun invoke(): UpdateCheck {
        if (!latestReleaseSource.isConfigured) return UpdateCheck.Unavailable

        val release = try {
            latestReleaseSource.fetchLatestRelease()
        } catch (error: IOException) {
            return UpdateCheck.Failed(UpdateCheckFailure.NETWORK)
        } catch (error: UpdateManifestFormatException) {
            return UpdateCheck.Failed(UpdateCheckFailure.MALFORMED)
        }

        return verdictFor(release)
    }

    private fun verdictFor(release: AppRelease): UpdateCheck = when {
        release.versionCode <= installedAppVersion.versionCode ->
            UpdateCheck.UpToDate(installedAppVersion.versionName)

        !isTrustedDownloadUrl(release.downloadUrl) ->
            UpdateCheck.Failed(UpdateCheckFailure.UNTRUSTED_DOWNLOAD_URL)

        else -> UpdateCheck.Available(release)
    }
}

/**
 * 내려받기 주소를 열어도 되는가.
 *
 * `https://github.com` 으로 시작하고, 그 뒤가 끝이거나 `/`로 이어져야 한다.
 *
 * **뒷조건이 핵심이다.** 접두사만 보면 다음이 모두 통과한다.
 * `https://github.com.attacker.example/...` (다른 호스트)
 * `https://github.com@attacker.example/...` (userinfo로 호스트를 가린 주소)
 *
 * 스킴과 호스트는 대소문자를 구분하지 않으므로 소문자로 바꿔 비교한다. 비교에만 쓰고
 * 실제로 여는 주소는 원본이다. `lowercase()`는 로캘에 영향받지 않는다.
 */
internal fun isTrustedDownloadUrl(url: String): Boolean {
    val trustedPrefix = HTTPS_SCHEME + TRUSTED_DOWNLOAD_HOST
    val normalized = url.lowercase()
    if (!normalized.startsWith(trustedPrefix)) return false

    val remainder = normalized.removePrefix(trustedPrefix)
    return remainder.isEmpty() || remainder.startsWith("/")
}
