package com.dksystems.tracephone.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 상대 시각으로 적는 가장 짧은 단위. 이보다 가까우면 '방금'이다. */
private val JUST_NOW_LIMIT: Duration = Duration.ofMinutes(1)

/** 분으로 적는 범위의 끝. */
private val MINUTES_LIMIT: Duration = Duration.ofHours(1)

/**
 * 상대 시각으로 적는 범위의 끝. 이보다 오래되면 날짜로 바꾼다.
 *
 * "31시간 전"은 머릿속에서 날짜로 옮겨야 뜻이 잡힌다. 날짜가 오늘이 아니라는 사실 자체가
 * 스스로 경고가 되므로, 색을 쓰지 않고도 눈에 띈다.
 */
private val RELATIVE_LIMIT: Duration = Duration.ofDays(1)

/**
 * 앱이 마지막으로 사용량을 확인한 지 얼마나 지났는가.
 *
 * ### 왜 '데이터의 나이'가 아니라 '확인한 시각'인가
 * 화면의 숫자는 **여러 날의 기록을 더한 값**이다. 동기화는 날짜마다 따로 저장하고
 * 실패한 날짜는 건너뛰므로, 한 달치 합계 안에는 서로 다른 시각에 쓰인 기록이 섞인다.
 * **집계에는 '한 수집 시각'이라는 것이 없다.**
 *
 * 그래서 가장 새것을 골라 "N 전에 수집한 기록"이라고 적으면 신선도를 과장하게 되고,
 * 가장 오래된 것을 고르면 늘 기간의 끝을 가리킨다. 어느 쪽도 집계를 옳게 설명하지 못한다.
 *
 * 대신 **실제로 일어난 사건**을 말한다. "3시간 전에 확인함"은 확인이 그때 있었다는 뜻이고,
 * 그 문장은 언제나 참이다. 오늘 데이터를 한 바이트도 쓰지 않아 저장할 것이 없었던 날에도
 * 확인은 일어났으므로 문장이 깨지지 않는다.
 *
 * 문자열을 담지 않는다. 분류는 여기서 테스트로 고정하고, 문구는 리소스에 두어
 * 번역할 때 한곳만 바뀌게 한다.
 */
sealed interface SyncAge {

    /** 한 번도 확인에 성공한 적이 없다. 첫 실행이거나 권한을 받기 전이다. */
    data object NeverChecked : SyncAge

    /** 1분 안. 시계가 뒤로 돌아가 미래 시각이 저장된 경우도 여기로 본다. */
    data object JustNow : SyncAge

    data class Minutes(val count: Int) : SyncAge

    data class Hours(val count: Int) : SyncAge

    /** 하루가 넘었다. 상대 시각 대신 날짜로 적는다. */
    data class Older(val checkedDate: LocalDate) : SyncAge
}

/**
 * 마지막으로 확인에 성공한 시각을 화면 단위로 분류한다.
 *
 * [clock] 하나로 '지금'과 시간대를 함께 받는다. `Instant`와 `ZoneId`로 나누면
 * [SyncAge.Older]의 날짜를 어느 시간대로 환산하는지가 호출부마다 갈릴 수 있다.
 *
 * **미래 시각을 '-1시간 전'으로 그리지 않는다.** 사용자가 기기 시간을 되돌리면
 * 저장된 시각이 미래가 되는데, 음수 시간을 적느니 '방금'이라고 적는 편이 덜 혼란스럽다.
 *
 * @param lastSuccessAtMillis 확인에 성공한 적이 없으면 null
 */
fun syncAgeOf(lastSuccessAtMillis: Long?, clock: Clock): SyncAge {
    if (lastSuccessAtMillis == null) {
        return SyncAge.NeverChecked
    }

    val elapsed = Duration.ofMillis(clock.millis() - lastSuccessAtMillis)

    return when {
        elapsed < JUST_NOW_LIMIT -> SyncAge.JustNow
        elapsed < MINUTES_LIMIT -> SyncAge.Minutes(elapsed.toMinutes().toInt())
        elapsed < RELATIVE_LIMIT -> SyncAge.Hours(elapsed.toHours().toInt())
        else -> SyncAge.Older(
            Instant.ofEpochMilli(lastSuccessAtMillis).atZone(clock.zone).toLocalDate()
        )
    }
}

/**
 * 다음에 다시 분류해 볼 때까지 기다릴 시간. null이면 기다릴 이유가 없다.
 *
 * 고정 간격으로 깨우지 않는다. 버킷이 굵어질수록 라벨이 바뀌는 간격도 길어지므로,
 * '시간' 단위에 들어선 뒤에도 1분마다 깨우는 것은 낭비다.
 *
 * [SyncAge.Older]와 [SyncAge.NeverChecked]에서는 **아예 깨어나지 않는다.**
 * 날짜 표기는 하루가 지나도 그대로이고, 확인이 실제로 일어나면 저장소 쪽에서 알려 온다.
 *
 * 분류를 여기 함께 둔 이유는 둘이 어긋나면 라벨이 늦게 바뀌기 때문이다.
 * 버킷을 늘리면서 이 함수를 잊으면 그 버킷은 영영 갱신되지 않는다.
 */
fun nextSyncAgeRefresh(age: SyncAge): Duration? = when (age) {
    SyncAge.JustNow -> Duration.ofSeconds(10)
    is SyncAge.Minutes -> Duration.ofMinutes(1)
    is SyncAge.Hours -> Duration.ofMinutes(10)
    SyncAge.NeverChecked, is SyncAge.Older -> null
}
