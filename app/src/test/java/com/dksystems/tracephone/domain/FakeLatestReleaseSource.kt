package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.update.LatestReleaseSource

/**
 * 최신 버전 기록을 지정한 대로 돌려주거나, 지정한 예외를 던진다.
 *
 * 실패를 예외로 알리는 인터페이스라 가짜도 예외를 던질 수 있어야 한다.
 */
class FakeLatestReleaseSource(
    override val isConfigured: Boolean = true,
    private val release: AppRelease? = null,
    private val error: Exception? = null
) : LatestReleaseSource {

    var fetchCount: Int = 0
        private set

    override suspend fun fetchLatestRelease(): AppRelease {
        fetchCount++
        error?.let { throw it }
        return requireNotNull(release) { "release 또는 error 중 하나는 있어야 한다" }
    }
}
