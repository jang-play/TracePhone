package com.dksystems.tracephone.domain.model

/**
 * 회선 종류별로, 다시 화면 사용 여부별로 나눈 사용량.
 *
 * ### 회선을 합치지 않는 이유
 * 요금이 붙는 쪽은 모바일뿐이다. 두 회선의 백그라운드를 합쳐서 보면
 * Wi-Fi로 크게 받는 앱에 가려 정작 요금이 새는 앱이 순위에서 밀린다.
 * 실측에서도 합산 기준 상위였던 앱이 모바일만 놓고 보면 대상이 아니었다.
 */
data class NetworkUsageSplit(
    val mobile: UsageStateSplit,
    val wifi: UsageStateSplit
) {

    val totalBytes: Long
        get() = mobile.totalBytes + wifi.totalBytes

    /** 회선을 가리지 않은 백그라운드 총량. 화면에 함께 적을 때 쓴다. */
    val backgroundBytes: Long
        get() = mobile.backgroundBytes + wifi.backgroundBytes

    /** 두 회선 중 하나라도 구분된 기록이 있는지. 없으면 백그라운드 표시 자체를 감춘다. */
    val hasKnownPortion: Boolean
        get() = mobile.hasKnownPortion || wifi.hasKnownPortion

    fun of(kind: NetworkKind): UsageStateSplit = when (kind) {
        NetworkKind.MOBILE -> mobile
        NetworkKind.WIFI -> wifi
    }

    /**
     * 한 회선이 사실상 전부를 차지하는가.
     *
     * 목록에서 막대와 회선별 숫자를 접을지 판단한다.
     * 111.44MB 중 Wi-Fi가 7KB라면 막대는 단색으로 그려지고
     * 회선별 숫자는 합계를 되풀이할 뿐이라 자리만 차지한다.
     *
     * 쓴 양이 아예 없을 때도 참으로 본다. 보여 줄 구성이 없다.
     */
    val isDominatedBySingleKind: Boolean
        get() {
            val total = totalBytes
            if (total <= 0L) {
                return true
            }

            val minorBytes = minOf(mobile.totalBytes, wifi.totalBytes)
            return minorBytes * PERCENT_SCALE < total * MINOR_KIND_PERCENT
        }

    companion object {

        /** 이 비율에 못 미치는 회선은 따로 보여 줄 값이 없다고 본다. */
        const val MINOR_KIND_PERCENT = 1

        private const val PERCENT_SCALE = 100L

        val ZERO = NetworkUsageSplit(mobile = UsageStateSplit.ZERO, wifi = UsageStateSplit.ZERO)
    }
}
