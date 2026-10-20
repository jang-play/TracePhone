package com.dksystems.tracephone.domain.update

import com.dksystems.tracephone.domain.model.AppRelease

/**
 * 최신 버전 기록을 가져오는 곳.
 *
 * 실패를 반환값이 아니라 예외로 알린다. 호출하는 쪽([CheckForUpdateUseCase])이
 * 이유별로 다른 문구를 보여야 해서, 실패를 한 종류로 뭉개면 안 되기 때문이다.
 */
interface LatestReleaseSource {

    /** 확인 주소가 빌드에 설정되어 있는가. 거짓이면 [fetchLatestRelease]를 부르지 않는다. */
    val isConfigured: Boolean

    /**
     * @throws java.io.IOException 연결·응답 실패
     * @throws UpdateManifestFormatException 응답 형식이 기대와 다름
     */
    suspend fun fetchLatestRelease(): AppRelease
}

/** 응답을 읽었으나 필요한 항목이 없거나 형식이 다를 때. */
class UpdateManifestFormatException(message: String) : Exception(message)
