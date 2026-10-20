package com.dksystems.tracephone.domain.model

/**
 * 사용량 경고 설정.
 *
 * 경고는 **모바일 데이터만** 대상으로 한다. Wi-Fi는 통상 종량이 아니라 경고할 이유가 없다.
 *
 * 한도는 두 갈래로 정한다. 목록에서 고른 값([presetLimitBytes])이 기본이고,
 * 직접 입력한 값([customLimitMegabytes])이 0보다 크면 그쪽이 우선한다.
 * 목록만 두면 원하는 값이 없을 때 막히고, 입력만 두면 매번 숫자를 쳐야 한다.
 *
 * @property customLimitMegabytes 직접 입력한 한도(MB). 0이면 쓰지 않는다는 뜻이다.
 */
data class UsageAlertSettings(
    val isEnabled: Boolean,
    val presetLimitBytes: Long,
    val customLimitMegabytes: Long
) {

    /** 실제로 적용되는 한도. 직접 입력한 값이 있으면 그것을, 없으면 고른 값을 쓴다. */
    val effectiveLimitBytes: Long
        get() = if (usesCustomLimit) {
            customLimitMegabytes * BYTES_PER_MEGABYTE
        } else {
            presetLimitBytes
        }

    val usesCustomLimit: Boolean
        get() = customLimitMegabytes > CUSTOM_LIMIT_DISABLED

    /** 이 사용량이 한도를 넘었는지. 한도와 같아도 넘은 것으로 본다. */
    /**
     * 한도의 몇 배를 넘겼는지. 아직 한 번도 못 넘겼으면 0.
     *
     * 한도가 50MB면 50MB에서 1, 100MB에서 2, 150MB에서 3이 된다.
     * 이 값이 지난번보다 커졌을 때만 알리므로, 한도를 넘길 때마다 다시 알리면서도
     * 같은 구간에서 반복해 알리는 일은 생기지 않는다.
     *
     * 한도가 0 이하면 몇 배인지 말할 수 없으므로 0으로 본다.
     */
    fun reachedMultipleBy(mobileBytes: Long): Int {
        val limit = effectiveLimitBytes
        if (limit <= 0L) {
            return NOT_REACHED
        }
        return (mobileBytes / limit).toInt()
    }

    companion object {

        /** 아직 한도를 한 번도 넘기지 않은 상태. */
        const val NOT_REACHED = 0

        /** 직접 입력을 쓰지 않음을 나타내는 값. */
        const val CUSTOM_LIMIT_DISABLED = 0L

        /** 화면에 표시하는 단위와 같은 1000 기준을 쓴다. [com.dksystems.tracephone.core.ByteFormatter] 참고. */
        const val BYTES_PER_MEGABYTE = 1_000_000L

        /**
         * 목록에서 고를 수 있는 한도.
         *
         * 자주 쓰는 값만 짧게 둔다. 그 밖의 값은 직접 입력으로 처리한다.
         */
        val PRESET_CHOICES_BYTES = listOf(
            100 * BYTES_PER_MEGABYTE,
            200 * BYTES_PER_MEGABYTE,
            300 * BYTES_PER_MEGABYTE
        )

        val DEFAULT = UsageAlertSettings(
            isEnabled = false,
            presetLimitBytes = PRESET_CHOICES_BYTES.last(),
            customLimitMegabytes = CUSTOM_LIMIT_DISABLED
        )
    }
}
