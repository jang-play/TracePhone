package com.dksystems.tracephone.data.local

import java.time.LocalDate

/**
 * 기간 내 앱 하나의 합계. `GROUP BY uid` 결과 한 줄에 대응한다.
 *
 * 모바일과 Wi-Fi를 하나의 행으로 합쳐 돌려주므로 앱당 쿼리 결과가 한 줄이다.
 * 코드에서 두 목록을 짝지어 합치는 과정이 사라진다.
 *
 * 포그라운드 몫과 '구분 없음' 몫도 회선별로 나눠 담는다.
 * 합쳐 담으면 화면에서 다시 나눌 방법이 없다.
 */
data class AppUsageTotalsRow(
    val uid: Int,
    val packageName: String?,
    val mobileReceivedBytes: Long,
    val mobileTransmittedBytes: Long,
    val mobileForegroundBytes: Long,
    val mobileUnknownStateBytes: Long,
    val wifiReceivedBytes: Long,
    val wifiTransmittedBytes: Long,
    val wifiForegroundBytes: Long,
    val wifiUnknownStateBytes: Long
)

/**
 * 앱 하나의 하루치 합계. `GROUP BY usageDate` 결과 한 줄에 대응한다.
 */
data class DailyUsageTotalsRow(
    val usageDate: LocalDate,
    val mobileReceivedBytes: Long,
    val mobileTransmittedBytes: Long,
    val wifiReceivedBytes: Long,
    val wifiTransmittedBytes: Long
)

/**
 * 앱 하나를 기간 전체에 걸쳐 회선별·상태별로 나눈 결과. 언제나 한 줄이다.
 *
 * 기록이 없으면 모든 값이 0이 된다.
 */
data class UsageStateSplitRow(
    val mobileTotalBytes: Long,
    val mobileForegroundBytes: Long,
    val mobileUnknownStateBytes: Long,
    val wifiTotalBytes: Long,
    val wifiForegroundBytes: Long,
    val wifiUnknownStateBytes: Long
)

/**
 * 모바일 사용량이 많은 앱 한 줄. 위젯처럼 상위 몇 개만 필요할 때 쓴다.
 */
data class TopMobileAppRow(
    val uid: Int,
    val packageName: String?,
    val mobileBytes: Long
)
