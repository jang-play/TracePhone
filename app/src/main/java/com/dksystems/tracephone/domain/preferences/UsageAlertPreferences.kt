package com.dksystems.tracephone.domain.preferences

import com.dksystems.tracephone.domain.model.UsageAlertSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 경고 설정과 '오늘 이미 알린 앱' 기록.
 *
 * 알림은 앱 하나당 하루 한 번만 보낸다. 15분마다 판정하는데 매번 알리면
 * 한도를 넘긴 앱이 하루 종일 알림을 쏟아내게 된다.
 */
interface UsageAlertPreferences {

    val settings: Flow<UsageAlertSettings>

    suspend fun setEnabled(enabled: Boolean)

    /** 목록에서 고른 한도를 저장한다. */
    suspend fun setPresetLimitBytes(limitBytes: Long)

    /** 직접 입력한 한도(MB)를 저장한다. 0이면 목록에서 고른 값을 쓴다는 뜻이다. */
    suspend fun setCustomLimitMegabytes(limitMegabytes: Long)

    /**
     * [date]에 이미 경고를 보낸 앱들의 UID.
     *
     * 저장된 기록이 다른 날짜의 것이면 빈 집합을 돌려준다.
     * 날짜별로 따로 쌓아두지 않으므로 오래된 기록이 남아 커지는 일이 없다.
     */
    /**
     * 그 날 앱마다 **마지막으로 알린 배수**. 아직 알린 적 없는 앱은 담기지 않는다.
     *
     * 저장된 날짜가 [date]와 다르면 빈 값으로 본다.
     * 날이 바뀌면 처음부터 다시 세야 하고, 지난 날 기록을 쌓아 둘 이유도 없다.
     */
    suspend fun notifiedMultiplesOn(date: LocalDate): Map<Int, Int>

    /**
     * 이번에 알린 앱들의 배수를 기록한다.
     *
     * 같은 날 앞서 기록한 다른 앱의 값은 그대로 두고, 넘겨받은 앱만 갱신한다.
     */
    suspend fun markNotified(date: LocalDate, notifiedMultiples: Map<Int, Int>)
}
