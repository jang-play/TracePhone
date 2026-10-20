package com.dksystems.tracephone.domain.model

/**
 * 시스템 통계를 DB로 옮기는 동기화의 결과.
 *
 * 권한 미허용은 예외가 아니라 **정상적으로 일어나는 상태**다.
 * 앱을 처음 설치하면 반드시 이 상태를 거치므로, 예외를 던지지 않고
 * [PermissionRequired]로 표현해 호출부가 권한 안내 화면으로 유도하게 한다.
 */
sealed interface UsageSyncResult {

    /**
     * @property syncedDayCount 조회에 성공한 날짜 수
     * @property recordCount 저장한 (날짜 × 앱 × 회선) 레코드 수
     */
    data class Success(
        val syncedDayCount: Int,
        val recordCount: Int
    ) : UsageSyncResult

    /** 사용 정보 접근 권한이 없어 조회 자체가 불가능한 상태. */
    data object PermissionRequired : UsageSyncResult

    /** 권한 외의 이유로 동기화가 실패한 상태. */
    data class Failure(val cause: Throwable) : UsageSyncResult
}
