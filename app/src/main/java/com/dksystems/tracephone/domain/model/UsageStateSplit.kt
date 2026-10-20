package com.dksystems.tracephone.domain.model

/**
 * 사용량을 "화면을 보고 있을 때"와 "그렇지 않을 때"로 나눈 값.
 *
 * ### 백그라운드를 저장하지 않고 계산하는 이유
 * 시스템은 포그라운드와 그 외를 나눠서 주고, 둘의 합은 총량과 정확히 일치한다(실기기 확인).
 * 세 값을 모두 저장하면 어느 하나가 어긋났을 때 무엇이 맞는지 판단할 근거가 없어진다.
 * 총량과 포그라운드만 사실로 두고 나머지는 뺄셈으로 얻는다.
 *
 * ### [unknownBytes]가 따로 있는 이유
 * 포그라운드 구분을 기록하기 시작한 시점보다 오래된 기록은 구분값이 없다.
 * 이를 0으로 취급하면 "전부 백그라운드"로 보이는데, 그것은 사실이 아니라 **모르는 것**이다.
 * 모르는 몫을 별도로 들고 다녀 화면에서 그대로 밝힌다.
 *
 * @property totalBytes 기간 내 총 사용량
 * @property foregroundBytes 그중 화면을 보고 있을 때 쓴 몫
 * @property unknownBytes 그중 구분 정보가 없는 몫
 */
data class UsageStateSplit(
    val totalBytes: Long,
    val foregroundBytes: Long,
    val unknownBytes: Long
) {

    /**
     * 화면을 보고 있지 않을 때 쓴 몫.
     *
     * 저장된 값이 서로 어긋나도 음수를 화면에 내보내지 않도록 0에서 자른다.
     */
    val backgroundBytes: Long
        get() = (totalBytes - foregroundBytes - unknownBytes).coerceAtLeast(0L)

    /** 구분 정보가 없는 기록이 섞여 있는지. 화면에 안내를 띄울지 판단한다. */
    val hasUnknownPortion: Boolean
        get() = unknownBytes > 0L

    /** 구분값이 있는 몫. 이 값이 0이면 비율을 말할 근거가 없다. */
    val measuredBytes: Long
        get() = (totalBytes - unknownBytes).coerceAtLeast(0L)

    /** 구분된 기록이 하나라도 있는지. 없으면 분리 표시 자체를 감춘다. */
    val hasKnownPortion: Boolean
        get() = measuredBytes > 0L

    /**
     * 전체 대비 백그라운드 비율. 0.0 ~ 1.0.
     *
     * 세 비율의 합이 1이 되므로 막대와 상세 화면의 백분율이 서로 맞는다.
     * 구분 없는 몫이 섞인 줄에서는 이 값을 화면에 적지 않는다.
     * 41%가 '모름'인데 '백그라운드 1%'라고 적으면 근거보다 단정적인 말이 된다.
     */
    val backgroundRatio: Float
        get() = ratioOf(backgroundBytes)

    /** 전체 대비 포그라운드 비율. 0.0 ~ 1.0. 사용량이 없으면 0. */
    val foregroundRatio: Float
        get() = ratioOf(foregroundBytes)

    /** 전체 대비 구분 없음 비율. 0.0 ~ 1.0. 사용량이 없으면 0. */
    val unknownRatio: Float
        get() = ratioOf(unknownBytes)

    private fun ratioOf(bytes: Long): Float =
        if (totalBytes <= 0L) 0f else bytes.toFloat() / totalBytes.toFloat()

    operator fun plus(other: UsageStateSplit): UsageStateSplit = UsageStateSplit(
        totalBytes = totalBytes + other.totalBytes,
        foregroundBytes = foregroundBytes + other.foregroundBytes,
        unknownBytes = unknownBytes + other.unknownBytes
    )

    companion object {
        val ZERO = UsageStateSplit(totalBytes = 0L, foregroundBytes = 0L, unknownBytes = 0L)

        /** 총량은 알지만 포그라운드 구분이 없는 기록. 업데이트 이전에 쌓인 자료가 여기에 해당한다. */
        fun allUnknown(totalBytes: Long): UsageStateSplit = UsageStateSplit(
            totalBytes = totalBytes,
            foregroundBytes = 0L,
            unknownBytes = totalBytes
        )
    }
}
