package com.dksystems.tracephone.domain.model

/**
 * 홈 화면 위젯이 한 번에 그릴 값 묶음.
 *
 * 위젯은 한 번 그려지고 나면 스스로 값을 더 가져오지 못한다.
 * 필요한 것을 한 번에 모아 넘겨야 부분만 갱신된 어중간한 화면이 생기지 않는다.
 *
 * @property mobileBytes 이번 주기의 모바일 사용량. 한도를 쓰지 않아도 이 값은 보여준다.
 * @property limitStatus 한도를 쓰는 경우의 진행 현황. 쓰지 않으면 null.
 * @property topApps 이번 주기에 모바일을 많이 쓴 앱들
 */
data class WidgetUsage(
    val cycle: UsagePeriod,
    val mobileBytes: Long,
    val limitStatus: MonthlyLimitStatus?,
    val topApps: List<AppUsage>
) {

    /** 한도를 정해 두어 진행률을 그릴 수 있는 상태인지. */
    val hasLimit: Boolean
        get() = limitStatus != null
}
