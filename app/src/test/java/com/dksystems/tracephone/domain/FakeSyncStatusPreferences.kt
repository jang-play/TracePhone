package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.preferences.SyncStatusPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 테스트용 확인 시각 저장소.
 *
 * 기록이 **일어났는지 아닌지**가 검증 대상이므로, 값을 돌려주는 것만으로는 모자라고
 * 호출 자체를 세어 둔다.
 */
class FakeSyncStatusPreferences(
    initialValue: Long? = null
) : SyncStatusPreferences {

    private val storedValue = MutableStateFlow(initialValue)

    /** [recordSuccessfulSync]가 불린 횟수. */
    var recordCount: Int = 0
        private set

    override val lastSuccessfulSyncAtMillis: Flow<Long?> = storedValue

    override suspend fun recordSuccessfulSync(atEpochMillis: Long) {
        recordCount++
        storedValue.value = atEpochMillis
    }

    /** 마지막으로 기록된 시각. 한 번도 기록되지 않았으면 초기값 그대로다. */
    val lastRecorded: Long?
        get() = storedValue.value
}
