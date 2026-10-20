package com.dksystems.tracephone.domain.model

/**
 * 앱을 밝게 볼지 어둡게 볼지.
 *
 * 기기 전체 설정과 **따로 둘 수 있게** 한다. 데이터 사용량은 숫자와 색으로 읽는 화면이라
 * 밝은 곳·어두운 곳에서 보기 편한 쪽이 기기 전체의 선택과 다를 수 있다.
 */
enum class ThemeMode {

    /** 기기의 야간 모드를 따른다. 기본값이다. */
    SYSTEM,

    LIGHT,

    DARK;

    /**
     * 지금 어두운 테마로 그려야 하는가.
     *
     * @param isSystemDark 기기가 야간 모드인지. [SYSTEM]일 때만 쓰인다.
     */
    fun resolvesToDark(isSystemDark: Boolean): Boolean = when (this) {
        SYSTEM -> isSystemDark
        LIGHT -> false
        DARK -> true
    }
}
