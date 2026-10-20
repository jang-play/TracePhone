package com.dksystems.tracephone.domain.model

/**
 * 앱 목록 정렬 기준. 모두 내림차순(많이 쓴 앱이 위)이다.
 */
enum class UsageSortOrder {
    TOTAL,
    MOBILE,
    WIFI,

    /**
     * 화면을 보고 있지 않을 때 **모바일로** 쓴 양이 많은 순.
     *
     * 회선을 합치지 않는다. 요금이 붙는 쪽은 모바일뿐이고,
     * 경고 알림도 모바일만 보므로 두 기능의 기준을 같게 둔다.
     */
    MOBILE_BACKGROUND;

    /** 정렬에 사용할 값을 꺼낸다. */
    fun sortKeyOf(usage: AppUsage): Long = when (this) {
        TOTAL -> usage.totalBytes
        MOBILE -> usage.mobile.totalBytes
        WIFI -> usage.wifi.totalBytes
        MOBILE_BACKGROUND -> usage.mobile.backgroundBytes
    }

    /**
     * 이 정렬에서 "쓴 적이 있다"고 볼 값. 0이면 목록에 둘 이유가 없다.
     *
     * ### [sortKeyOf]와 일부러 다르다
     * `MOBILE_BACKGROUND`의 정렬 키는 포그라운드 구분이 기록되지 않은 앱에 **0을 돌려준다.**
     * 그 앱들은 모바일을 분명히 썼는데 구분만 모르는 것이라, 정렬 키로 거르면 사라진다.
     * 그래서 백그라운드순에서도 '모바일을 썼는가'로 판단한다.
     *
     * 같은 함정이 "큰 숫자를 정렬 기준값으로 바꾸기"를 기각할 때도 근거가 됐다.
     * 두 함수를 합치려는 시도가 보이면 이 주석을 먼저 읽어야 한다.
     */
    fun visibleBytesOf(usage: AppUsage): Long = when (this) {
        TOTAL -> usage.totalBytes
        MOBILE, MOBILE_BACKGROUND -> usage.mobile.totalBytes
        WIFI -> usage.wifi.totalBytes
    }
}
