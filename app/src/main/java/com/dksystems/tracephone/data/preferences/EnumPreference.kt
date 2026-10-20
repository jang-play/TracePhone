package com.dksystems.tracephone.data.preferences

import com.dksystems.tracephone.core.AppLog

/**
 * 저장된 이름을 열거형으로 되돌린다.
 *
 * **저장 값은 이름 문자열이다.** 순서(ordinal)를 쓰면 나중에 항목을 끼워 넣었을 때
 * 저장해 둔 값이 엉뚱한 항목을 가리키게 된다.
 *
 * 항목이 사라진 뒤에 옛 값이 남아 있을 수 있으므로, 알아볼 수 없는 값은 조용히 기본값으로 돌린다.
 * 설정 하나 때문에 화면이 뜨지 않는 것이 더 나쁘다.
 */
internal inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
    if (this == null) {
        return default
    }

    return runCatching { enumValueOf<T>(this) }.getOrElse {
        AppLog.warn("Unknown preference value '$this'; 기본값 $default 을 사용합니다.")
        default
    }
}
