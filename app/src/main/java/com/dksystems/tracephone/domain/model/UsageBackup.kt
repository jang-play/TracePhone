package com.dksystems.tracephone.domain.model

/**
 * 누적 기록을 파일로 꺼내거나 되돌리는 일의 상태.
 *
 * 성공을 **행 수와 함께** 알린다. "완료했습니다"만으로는 빈 파일을 내보냈는지,
 * 아무것도 들어가지 않았는지 알 수 없다. 이 기능은 복구할 수 없는 기록을 다루므로
 * 사용자가 숫자로 확인할 수 있어야 한다.
 */
sealed interface UsageBackupState {

    data object Idle : UsageBackupState

    /** 파일로 쓰고 있다. */
    data object Exporting : UsageBackupState

    /** 파일을 읽어 넣고 있다. */
    data object Importing : UsageBackupState

    data class Exported(val rowCount: Int) : UsageBackupState

    data class Imported(val rowCount: Int) : UsageBackupState

    data class Failed(val reason: UsageBackupFailure) : UsageBackupState
}

/**
 * 실패한 이유.
 *
 * 사용자가 할 수 있는 일이 서로 달라 구분한다. [MALFORMED]는 **다른 파일을 고르라**는
 * 뜻이고, [STORAGE]는 다시 시도해 볼 만하다.
 */
enum class UsageBackupFailure {

    /** TracePhone 백업 파일이 아니거나 내용이 망가졌다. */
    MALFORMED,

    /** 파일을 읽거나 쓸 수 없었다. */
    STORAGE,

    /** 예상하지 못한 실패. 로그에 실제 원인이 남는다. */
    UNKNOWN
}
