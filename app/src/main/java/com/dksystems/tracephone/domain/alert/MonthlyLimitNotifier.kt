package com.dksystems.tracephone.domain.alert

import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold

/**
 * 요금제 한도 알림 발송 창구.
 *
 * 판정 로직이 안드로이드 알림 API에 묶이지 않도록 인터페이스로 분리한다.
 */
fun interface MonthlyLimitNotifier {

    fun notifyThresholdReached(status: MonthlyLimitStatus, threshold: MonthlyLimitThreshold)
}
