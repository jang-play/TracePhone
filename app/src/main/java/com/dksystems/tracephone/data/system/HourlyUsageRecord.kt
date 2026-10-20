package com.dksystems.tracephone.data.system

import com.dksystems.tracephone.domain.model.NetworkKind
import com.dksystems.tracephone.domain.model.UsageBytes

/**
 * 시스템에서 읽어 시간대별로 나눈 사용량 한 줄.
 *
 * @property hour 0부터 23까지
 */
data class HourlyUsageRecord(
    val hour: Int,
    val networkKind: NetworkKind,
    val bytes: UsageBytes
)
