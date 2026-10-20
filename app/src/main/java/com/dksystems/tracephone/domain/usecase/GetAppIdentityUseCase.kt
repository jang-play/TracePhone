package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import javax.inject.Inject

/**
 * UID로 앱의 표시 정보를 구한다. 상세 화면 제목에 사용한다.
 */
class GetAppIdentityUseCase @Inject constructor(
    private val systemUsageReader: SystemUsageReader
) {

    suspend operator fun invoke(uid: Int): AppIdentity = systemUsageReader.findAppIdentity(uid)
}
