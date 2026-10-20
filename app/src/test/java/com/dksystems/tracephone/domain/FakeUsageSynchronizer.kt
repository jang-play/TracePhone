package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.model.UsageSyncResult
import com.dksystems.tracephone.domain.repository.UsageSynchronizer

/** 테스트용 동기화. 어떤 일수로 불렸는지 기록해 백필 길이 판단을 검증한다. */
class FakeUsageSynchronizer(
    private val syncResult: UsageSyncResult = UsageSyncResult.Success(0, 0)
) : UsageSynchronizer {

    /** 마지막으로 [syncRecentDays]에 전달된 일수. */
    var lastRequestedSyncDayCount: Long? = null
        private set

    override suspend fun syncRecentDays(dayCount: Long): UsageSyncResult {
        lastRequestedSyncDayCount = dayCount
        return syncResult
    }
}
