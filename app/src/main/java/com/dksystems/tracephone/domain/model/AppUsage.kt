package com.dksystems.tracephone.domain.model

/**
 * 특정 기간 동안 앱 하나가 사용한 데이터량.
 *
 * 모바일과 Wi-Fi를 합치지 않고 끝까지 분리해서 들고 간다.
 * 화면에서 둘의 비율을 보여주는 것이 이 앱의 핵심이기 때문이다.
 * 화면 사용 여부(직접 사용 / 백그라운드) 역시 회선별로 따로 들고 간다.
 */
data class AppUsage(
    val app: AppIdentity,
    val usage: NetworkUsageSplit
) {

    /** 회선별 사용량에 짧게 닿기 위한 접근자. `usage.mobile`을 매번 적지 않게 한다. */
    val mobile: UsageStateSplit
        get() = usage.mobile

    val wifi: UsageStateSplit
        get() = usage.wifi

    val totalBytes: Long
        get() = usage.totalBytes

    /** 회선 종류별 사용량을 꺼낸다. 정렬 기준을 동적으로 바꿀 때 사용한다. */
    fun bytesOf(kind: NetworkKind): Long = usage.of(kind).totalBytes
}
