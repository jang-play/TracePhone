package com.dksystems.tracephone.data.system

/**
 * 시스템이 준 통계 구간을 시간대별로 나눈다.
 *
 * ### 왜 나눠야 하는가
 * 시스템은 사용량을 시간 단위로 기록하지 않는다. 기기마다 다르지만 보통 **두 시간짜리 구간**으로
 * 묶어 두므로, 한 구간이 여러 시간대에 걸친다.
 * 시간별로 보여주려면 겹치는 만큼 비례해서 나눠 담는 수밖에 없다.
 *
 * **따라서 시간별 값은 근사치다.** 구간 안에서 언제 얼마나 썼는지는 시스템도 모른다.
 * 화면에서도 이 점을 밝힌다.
 *
 * 나눈 값의 합은 원래 값과 정확히 같다. 나머지를 가장 많이 겹친 시간대에 몰아주기 때문이다.
 * 그렇게 하지 않으면 시간별 합계와 하루 합계가 어긋나 보인다.
 */
object HourlyBucketSplitter {

    const val HOURS_PER_DAY = 24

    private const val MILLIS_PER_HOUR = 60L * 60L * 1000L

    /**
     * @param bucketStartMillis 시스템 구간의 시작 시각
     * @param bucketEndMillis 시스템 구간의 끝 시각 (미포함)
     * @param bytes 그 구간의 사용량
     * @param dayStartMillis 나눠 담을 날의 0시
     * @return 시간대(0~23) -> 바이트. 겹치지 않는 시간대는 담기지 않는다.
     */
    fun split(
        bucketStartMillis: Long,
        bucketEndMillis: Long,
        bytes: Long,
        dayStartMillis: Long
    ): Map<Int, Long> {
        val bucketDurationMillis = bucketEndMillis - bucketStartMillis
        if (bucketDurationMillis <= 0L || bytes <= 0L) {
            return emptyMap()
        }

        val overlapByHour = overlapMillisByHour(bucketStartMillis, bucketEndMillis, dayStartMillis)
        if (overlapByHour.isEmpty()) {
            return emptyMap()
        }

        val totalOverlapMillis = overlapByHour.values.sum()
        if (totalOverlapMillis <= 0L) {
            return emptyMap()
        }

        // 겹친 부분에 비례해 나눈다. 구간이 하루 밖으로 삐져나간 만큼은 자연히 빠진다.
        val bytesByHour = overlapByHour.mapValues { (_, overlapMillis) ->
            bytes * overlapMillis / totalOverlapMillis
        }

        return assignRemainder(bytesByHour, overlapByHour, bytes)
    }

    private fun overlapMillisByHour(
        bucketStartMillis: Long,
        bucketEndMillis: Long,
        dayStartMillis: Long
    ): Map<Int, Long> {
        val overlaps = mutableMapOf<Int, Long>()

        for (hour in 0 until HOURS_PER_DAY) {
            val hourStart = dayStartMillis + hour * MILLIS_PER_HOUR
            val hourEnd = hourStart + MILLIS_PER_HOUR

            val overlapMillis =
                minOf(bucketEndMillis, hourEnd) - maxOf(bucketStartMillis, hourStart)

            if (overlapMillis > 0L) {
                overlaps[hour] = overlapMillis
            }
        }

        return overlaps
    }

    /**
     * 정수 나눗셈에서 버려진 나머지를 가장 많이 겹친 시간대에 더한다.
     *
     * 하루 밖으로 삐져나간 구간은 애초에 전부를 담지 않으므로, 담긴 몫만큼만 채운다.
     */
    private fun assignRemainder(
        bytesByHour: Map<Int, Long>,
        overlapByHour: Map<Int, Long>,
        bytes: Long
    ): Map<Int, Long> {
        val distributedBytes = bytesByHour.values.sum()
        val remainder = bytes - distributedBytes
        if (remainder <= 0L) {
            return bytesByHour
        }

        val largestOverlapHour = overlapByHour.maxByOrNull { it.value }?.key ?: return bytesByHour

        return bytesByHour.toMutableMap().apply {
            this[largestOverlapHour] = (this[largestOverlapHour] ?: 0L) + remainder
        }
    }
}
