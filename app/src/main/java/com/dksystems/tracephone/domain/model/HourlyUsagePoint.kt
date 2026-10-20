package com.dksystems.tracephone.domain.model

/**
 * 하루 안의 한 시간대 사용량.
 *
 * 시스템은 사용량을 시간 단위로 기록하지 않고 더 긴 구간으로 묶어 두므로,
 * 여기 담긴 값은 겹친 시간에 비례해 나눈 **근사치**다.
 * 다만 24개를 모두 더하면 그 날의 실제 합계와 같다.
 *
 * @property hour 0부터 23까지
 */
data class HourlyUsagePoint(
    val hour: Int,
    val mobile: UsageBytes,
    val wifi: UsageBytes
) {

    val totalBytes: Long
        get() = mobile.totalBytes + wifi.totalBytes
}
