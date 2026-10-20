package com.dksystems.tracephone.domain.preferences

import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 요금제 한도 설정과, 이번 주기에 이미 알린 지점을 기억한다.
 */
interface MonthlyLimitPreferences {

    val settings: Flow<MonthlyLimitSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setLimitMegabytes(limitMegabytes: Long)

    suspend fun setCycleStartDay(cycleStartDay: Int)

    /**
     * 해당 주기에 이미 알린 지점들.
     *
     * 저장된 주기 시작일이 [cycleStart]와 다르면 빈 집합으로 본다.
     * 주기가 바뀌면 다시 알려야 하고, 지난 주기 기록을 쌓아 둘 이유도 없다.
     */
    suspend fun notifiedThresholdsIn(cycleStart: LocalDate): Set<MonthlyLimitThreshold>

    suspend fun markNotified(cycleStart: LocalDate, threshold: MonthlyLimitThreshold)
}
