package com.dksystems.tracephone.domain.model

import java.time.LocalDate

/**
 * 요금제 한도 설정.
 *
 * 모바일만 센다. Wi-Fi는 통상 종량이 아니어서 요금제 한도와 관계가 없다.
 *
 * @property limitMegabytes 주기당 한도. 0이면 아직 정하지 않은 것으로 본다.
 * @property cycleStartDay 매월 며칠에 주기가 초기화되는지
 */
data class MonthlyLimitSettings(
    val isEnabled: Boolean,
    val limitMegabytes: Long,
    val cycleStartDay: Int
) {

    val limitBytes: Long
        get() = limitMegabytes * BYTES_PER_MEGABYTE

    /** 한도 값이 정해져 있는지. 켜져 있어도 값이 0이면 보여줄 것이 없다. */
    val isConfigured: Boolean
        get() = limitMegabytes > NO_LIMIT

    /** 켜져 있고 값도 정해져 화면과 알림에서 쓸 수 있는 상태인지. */
    val isActive: Boolean
        get() = isEnabled && isConfigured

    fun cycleContaining(today: LocalDate): UsagePeriod =
        BillingCycle.periodContaining(today, cycleStartDay)

    companion object {
        const val NO_LIMIT = 0L

        /** 안드로이드가 용량을 1000 기준으로 표시하므로 여기서도 1000을 쓴다. */
        const val BYTES_PER_MEGABYTE = 1_000_000L
        const val MEGABYTES_PER_GIGABYTE = 1_000L

        /** 입력값이 바이트로 환산될 때 넘치지 않도록 상한을 둔다. 1000 GB. */
        const val MAX_LIMIT_MEGABYTES = 1_000_000L

        val DEFAULT = MonthlyLimitSettings(
            isEnabled = false,
            limitMegabytes = NO_LIMIT,
            cycleStartDay = BillingCycle.DEFAULT_START_DAY
        )
    }
}

/**
 * 한도 대비 어디까지 왔는지 알리는 지점.
 *
 * 다 쓰고 나서 알리면 늦으므로 미리 여러 번 알린다.
 * 80%만으로는 늦을 때가 있다 — 실측에서 한 주기는 11일째에야 80%에 닿았는데
 * 그 주기의 최종 사용량은 한도의 1.5배였다.
 *
 * ### 이름을 바꾸면 안 된다
 * `MonthlyLimitPreferencesDataStore`가 **이미 알린 지점을 이 열거형의 이름 문자열로 저장한다.**
 * [WARNING]이나 [REACHED]의 이름을 바꾸면 저장된 값이 어느 지점도 가리키지 못하게 되어
 * **이번 주기에 이미 알린 지점을 다시 알린다.**
 *
 * 그래서 이름이 고르지 않다([HALF]·[WARNING]·[ALMOST]·[REACHED]).
 * 저장 호환이 이름의 일관성보다 앞선다.
 *
 * 항목을 **더하는** 것은 안전하다. 판정([MonthlyLimitStatus.reachedThreshold])이
 * `entries`를 돌고, 중복 방지는 이름 집합으로 하므로 둘 다 따라온다.
 */
enum class MonthlyLimitThreshold(val percent: Int) {
    HALF(50),
    WARNING(80),
    ALMOST(90),
    REACHED(100);

    /** 이 지점을 넘겼는지. 한도가 0이면 판단할 근거가 없으므로 false. */
    fun isReachedBy(usedBytes: Long, limitBytes: Long): Boolean =
        limitBytes > 0L && usedBytes * PERCENT_SCALE >= limitBytes * percent

    private companion object {
        const val PERCENT_SCALE = 100L
    }
}
