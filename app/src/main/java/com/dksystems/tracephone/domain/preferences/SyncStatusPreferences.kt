package com.dksystems.tracephone.domain.preferences

import kotlinx.coroutines.flow.Flow

/**
 * 사용량 확인이 마지막으로 성공한 시각.
 *
 * ### 왜 사용량 DB가 아니라 여기에 두는가
 * `daily_app_usage`의 `recordedAtEpochMillis`는 '행을 쓴 시각'이라, 저장할 것이 없었던 날
 * (예: 데이터를 한 바이트도 쓰지 않은 날)에는 앞으로 가지 않는다. 그러면 정상으로 확인한
 * 상태와 확인하지 못한 상태를 구별할 수 없다.
 *
 * **성공한 확인은 저장할 것이 없어도 일어난 사건**이므로 따로 남긴다.
 */
interface SyncStatusPreferences {

    /** 확인에 성공한 적이 없으면 null을 내보낸다. */
    val lastSuccessfulSyncAtMillis: Flow<Long?>

    suspend fun recordSuccessfulSync(atEpochMillis: Long)
}
