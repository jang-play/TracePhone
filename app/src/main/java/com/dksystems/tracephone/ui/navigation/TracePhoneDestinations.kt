package com.dksystems.tracephone.ui.navigation

import com.dksystems.tracephone.domain.model.UsagePeriod
import java.time.LocalDate

/**
 * 화면 경로 정의.
 *
 * 경로 문자열을 화면 코드에 흩뿌리지 않고 여기서만 만든다.
 * 인자 이름을 바꿀 때 고칠 곳이 한 군데로 유지된다.
 */
object TracePhoneDestinations {

    const val SUMMARY_ROUTE = "summary"

    const val SETTINGS_ROUTE = "settings"

    /**
     * 지난 청구 주기들의 실적을 보는 화면.
     *
     * 인자가 없다. 보여줄 주기는 한도 설정에서 정해지므로 화면이 스스로 알아낸다.
     * 요약 화면의 한도 카드와 설정 화면 양쪽에서 같은 경로로 들어온다.
     */
    const val CYCLE_HISTORY_ROUTE = "cycle_history"

    const val ARG_UID = "uid"
    const val ARG_START_DATE = "startDate"
    const val ARG_END_DATE = "endDate"

    const val ARG_DATE = "date"

    const val APP_DETAIL_ROUTE = "app_detail/{$ARG_UID}/{$ARG_START_DATE}/{$ARG_END_DATE}"

    const val HOURLY_USAGE_ROUTE = "hourly/{$ARG_UID}/{$ARG_DATE}"

    /** 앱 하나의 특정 날짜를 시간대별로 보는 화면. 일자별 그래프에서 막대를 눌러 들어온다. */
    fun hourlyUsage(uid: Int, date: LocalDate): String = "hourly/$uid/$date"

    /**
     * 앱 상세 화면 경로를 만든다.
     *
     * 기간을 단위 이름이 아니라 **실제 날짜 범위**로 넘긴다.
     * 사용자가 몇 주 전으로 이동한 상태에서도 요약 화면과 상세 화면의 수치가 어긋나지 않아야 하는데,
     * 단위 이름만으로는 '몇 칸 전인지'를 표현할 수 없기 때문이다.
     *
     * 날짜는 ISO 형식(`2026-09-03`)이라 경로에 그대로 넣어도 안전하다.
     */
    fun appDetail(uid: Int, period: UsagePeriod): String =
        "app_detail/$uid/${period.startDate}/${period.endDateInclusive}"
}
