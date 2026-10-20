package com.dksystems.tracephone.domain.model

import java.time.LocalDate

/**
 * 일자별 추이 그래프의 한 점.
 *
 * 앱 상세 화면에서 하루 단위 막대 하나에 대응한다.
 */
data class DailyUsagePoint(
    val date: LocalDate,
    val mobile: UsageBytes,
    val wifi: UsageBytes
) {

    val totalBytes: Long
        get() = mobile.totalBytes + wifi.totalBytes
}
