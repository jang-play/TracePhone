package com.dksystems.tracephone.domain.model

/**
 * 앱별 목록의 막대가 쓰는 자. **모든 줄이 같은 자를 써야 앱끼리 비교된다.**
 *
 * 줄 하나만 보고는 정할 수 없어 목록 전체와 한도 설정을 함께 봐야 한다.
 *
 * ### 자는 그 기간의 모바일 합계다
 * 가득 찬 막대가 '이 기간에 쓴 모바일 전부'를 뜻하므로 **막대 길이가 곧 그 앱의 몫**이다.
 * 줄에 적히는 백분율과 분모가 같아 둘이 같은 말을 한다.
 *
 * 가장 큰 앱을 자로 삼으면 1등 줄이 늘 가득 찬다. 그러면 막대가 순위만 말하고 크기는
 * 말하지 않는다 — 조용한 날의 2 MB짜리 앱도 막대가 꽉 찬다. 게다가 줄에 몫을 함께 적으면
 * **한 줄 안에서 두 표시가 어긋난다**(`54%`라고 적어 놓고 막대는 가득 참).
 *
 * ### 하루 기준을 자로 삼지 않는 이유
 * 그렇게 만들었다가 실기기에서 무너졌다 — 한도 6 GB / 30일이면 하루 기준이 200 MB인데
 * 그날 가장 많이 쓴 앱이 7.37 MB였다. 모든 막대가 4% 이하가 되어 비교라는 목적이 사라졌다.
 * **데이터와 무관한 외부 상수는 자가 될 수 없다.** 합계를 쓰면 1등 막대가 구조적으로
 * `1 / 앱 수` 밑으로 내려가지 않는다. 하루 기준은 [referenceFraction] 눈금으로 옮겼다.
 *
 * @property maxBytes 막대가 가득 찼을 때의 값. 0이면 잴 것이 없다는 뜻이다.
 * @property referenceBytes 눈금을 세울 자리. 0이면 기준이 없다. [dailyReferenceBytesOf] 참고.
 */
data class AppListScale(val maxBytes: Long, val referenceBytes: Long = 0L) {

    /**
     * 막대를 얼마나 채울지. 0.0 ~ 1.0.
     *
     * [maxBytes]가 0인 경우는 목록이 비었거나 아무도 모바일을 쓰지 않은 때다.
     * 나눌 수 없으므로 빈 막대를 돌려준다.
     */
    fun fractionOf(bytes: Long): Float {
        if (maxBytes <= 0L) {
            return 0f
        }

        return (bytes.toFloat() / maxBytes).coerceIn(0f, 1f)
    }

    /**
     * 하루 기준 눈금의 자리. 그릴 수 없으면 null.
     *
     * 이 길이만큼 자란 막대는 하루치를 혼자 다 쓴 앱이라는 뜻이다.
     *
     * **기준이 자를 넘으면 그리지 않는다.** 그 날은 앱을 다 합쳐도 하루치에 못 미쳤다는 뜻이라
     * 눈금을 세울 자리가 없다. 오른쪽 끝에 붙여 놓으면 '오늘 쓴 만큼이 곧 하루치'로 읽혀
     * 정반대가 된다. 눈금이 없는 것 자체가 '오늘은 하루치에 닿지 않았다'는 말이다.
     */
    val referenceFraction: Float?
        get() = if (referenceBytes in 1..maxBytes) {
            referenceBytes.toFloat() / maxBytes
        } else {
            null
        }
}

/**
 * 하루에 써도 되는 양. 기준을 세울 수 없으면 0.
 *
 * ### 왜 한도를 주기 일수로 나누는가
 * '남은 양 ÷ 남은 날수'를 쓰면 많이 쓴 다음 날 기준이 줄어들어, 같은 사용량인데도
 * 눈금이 갑자기 왼쪽으로 옮겨 간다. 어제와 오늘을 견줄 수 없게 된다. 주기 내내 고정된 값이어야 한다.
 *
 * ### 왜 '일' 보기에서만 쓰는가
 * 주나 월을 보는데 하루치에 눈금을 세우면 거의 모든 앱이 그 왼쪽에 놓여, 눈금이 맨 앞에 붙은 채
 * 아무것도 말하지 않는다. 기준이 있는 척하면서 실제로는 없는 상태가 가장 나쁘다.
 */
fun dailyReferenceBytesOf(unit: PeriodUnit, limit: MonthlyLimitStatus?): Long {
    if (unit != PeriodUnit.DAY || limit == null) {
        return 0L
    }

    val dayCount = limit.cycle.dayCount
    if (limit.limitBytes <= 0L || dayCount <= 0) {
        return 0L
    }

    return limit.limitBytes / dayCount
}
