package com.dksystems.tracephone.domain.alert

import com.dksystems.tracephone.domain.model.AppIdentity

/**
 * 한도를 넘긴 앱을 사용자에게 알린다.
 *
 * 인터페이스로 분리해 두면 판정 로직을 실제 알림 없이 테스트할 수 있다.
 */
fun interface UsageAlertNotifier {

    /**
     * @param app 한도를 넘긴 앱
     * @param usedMobileBytes 오늘 이 앱이 쓴 모바일 데이터
     * @param limitBytes 설정된 하루 한도
     */
    fun notifyDailyLimitExceeded(app: AppIdentity, usedMobileBytes: Long, limitBytes: Long)
}
