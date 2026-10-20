package com.dksystems.tracephone.core

import java.util.Locale

/**
 * 바이트 수를 사람이 읽는 문자열로 바꾼다.
 *
 * ### 왜 1000 기준인가
 * Android 시스템 UI는 `android.text.format.Formatter.formatFileSize`를 쓰고,
 * 이 함수는 **1000 단위 SI 접두어**를 사용한다.
 * 실기기에서 확인한 결과 `986,389,338` 바이트를 시스템은 `0.99GB`로 표시한다.
 *
 * 1024 기준으로 표시하면 같은 바이트 수가 설정 화면보다 작게 보인다.
 * 위 값의 경우 940.69 MB 대 986.39 MB로 **45.7 MB나 어긋나** 앱이 사용량을
 * 놓치고 있는 것처럼 읽힌다. 설정 화면과의 교차 검증이 이 앱의 신뢰성 근거이므로
 * 시스템과 같은 규약을 따른다.
 *
 * 예) `1_331_691_520` -> `"1.33 GB"`
 */
/**
 * 값과 단위를 나눈 결과.
 *
 * @property value 숫자 부분. 예) `"146.96"`
 * @property unit 단위 부분. 예) `"MB"`
 */
data class FormattedBytes(val value: String, val unit: String)

object ByteFormatter {

    private const val UNIT_STEP = 1000.0

    /** 1000씩 올라가는 SI 단위 이름. 인덱스가 곧 1000의 거듭제곱 횟수다. */
    private val UNIT_LABELS = listOf("B", "KB", "MB", "GB", "TB", "PB")

    /** 소수점을 붙이기 시작하는 단위 인덱스. 바이트와 킬로바이트는 정수로 충분하다. */
    private const val FIRST_UNIT_WITH_DECIMALS = 2

    private const val DECIMAL_PLACES = 2

    private const val UNIT_SEPARATOR = ' '
    private const val DECIMAL_POINT = '.'

    /**
     * @param byteCount 표시할 바이트 수. 음수는 0으로 취급한다.
     * @return "0 B", "512 KB", "1.24 GB" 형태의 문자열
     */
    fun format(byteCount: Long): String {
        if (byteCount <= 0L) {
            return "0 ${UNIT_LABELS.first()}"
        }

        var value = byteCount.toDouble()
        var unitIndex = 0

        while (value >= UNIT_STEP && unitIndex < UNIT_LABELS.lastIndex) {
            value /= UNIT_STEP
            unitIndex++
        }

        val label = UNIT_LABELS[unitIndex]
        return if (unitIndex < FIRST_UNIT_WITH_DECIMALS) {
            String.format(Locale.US, "%.0f %s", value, label)
        } else {
            String.format(Locale.US, "%.${DECIMAL_PLACES}f %s", value, label)
        }
    }

    /**
     * 값과 단위를 나눠 돌려준다.
     *
     * 화면에서 단위를 값과 다른 글꼴·크기로 그리려면 둘을 따로 받아야 한다.
     * 이유는 [com.dksystems.tracephone.ui.component.ByteText]에 적어 두었다.
     */
    fun split(byteCount: Long): FormattedBytes {
        val formatted = format(byteCount)
        val unitSeparatorIndex = formatted.indexOf(UNIT_SEPARATOR)
        if (unitSeparatorIndex < 0) {
            return FormattedBytes(value = formatted, unit = "")
        }

        return FormattedBytes(
            value = formatted.substring(0, unitSeparatorIndex),
            unit = formatted.substring(unitSeparatorIndex + 1)
        )
    }

    /**
     * 값이 여러 개 나열되는 곳(그래프 축 눈금 등)에 쓴다. 의미 없는 소수점 이하 0을 떼어낸다.
     *
     * `2.00 GB` -> `2 GB` · `1.50 GB` -> `1.5 GB` · `986.39 MB` -> 그대로
     *
     * 소수점을 **전부** 없애지는 않는다. 눈금 간격에 따라 1.5 GB 지점에 격자선이 그어질 수 있는데,
     * 이를 `2 GB`로 적으면 선의 위치와 라벨이 어긋나 그래프를 잘못 읽게 된다.
     */
    fun formatCompact(byteCount: Long): String {
        val formatted = format(byteCount)

        val unitSeparatorIndex = formatted.indexOf(UNIT_SEPARATOR)
        if (unitSeparatorIndex < 0) {
            return formatted
        }

        val numberPart = formatted.substring(0, unitSeparatorIndex)
        if (!numberPart.contains(DECIMAL_POINT)) {
            return formatted
        }

        val trimmedNumber = numberPart.trimEnd('0').trimEnd(DECIMAL_POINT)
        return trimmedNumber + formatted.substring(unitSeparatorIndex)
    }
}
