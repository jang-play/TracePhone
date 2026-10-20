package com.dksystems.tracephone.ui.summary

import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.UsageSortOrder

/** 백분율로 옮길 때 쓰는 배수. */
private const val PERCENT_SCALE = 100L

/**
 * 이 앱이 목록 전체에서 차지하는 몫(%).
 *
 * **버림으로 계산한다.** 0.9%를 1%로 올려 적으면 그런 앱이 수십 개일 때 합이 100%를 훌쩍 넘어
 * 사용자가 숫자를 믿지 않게 된다. 0이 나온 몫은 화면에서 `<1%`로 적어 '없음'과 구분한다.
 *
 * @param listTotalBytes 목록에 있는 모든 앱의 합. 0이면 나눌 수 없으므로 0을 돌려준다.
 */
internal fun usageSharePercent(usageBytes: Long, listTotalBytes: Long): Int {
    if (listTotalBytes <= 0L) {
        return 0
    }

    return ((usageBytes * PERCENT_SCALE) / listTotalBytes).toInt()
}

/**
 * 큰 숫자 옆의 작은 값으로 Wi-Fi 대신 백그라운드를 적어야 하는가.
 *
 * 백그라운드순으로 세워 놓고 백그라운드 값을 감추면 순서를 읽을 방법이 없다.
 * 반대로 포그라운드 구분이 기록되기 전의 값은 백그라운드 몫을 알 수 없어,
 * 0으로 꾸며 적으면 '뒤에서 아무것도 안 썼다'는 거짓말이 된다.
 *
 * 조용히 틀리는 판단이라 순수 함수로 빼 두었다 - 틀려도 화면은 그려지고,
 * 정렬 기준만 보이지 않게 된다.
 */
internal fun showsBackgroundValue(sortOrder: UsageSortOrder, hasKnownPortion: Boolean): Boolean =
    sortOrder == UsageSortOrder.MOBILE_BACKGROUND && hasKnownPortion

/**
 * 목록 한 줄을 스크린 리더가 읽을 문구로 잇는 데 필요한 글자들.
 *
 * 리소스에서 이미 꺼낸 문자열만 받는다. 안드로이드에 기대지 않아야 JVM 테스트로 고정할 수 있다.
 */
internal data class AppUsageRowLabels(
    val mobile: String,
    val share: String,
    val separator: String,
    val notInstalled: String
)

/**
 * 목록 한 줄을 한 문장으로 잇는다.
 *
 * 아이콘·막대·라벨 없는 숫자들이 따로 읽히면 어느 값이 무엇인지 알 수 없다.
 * 예) `"카카오톡, 모바일 800 MB, 전체의 22%, Wi-Fi 440 MB"`
 *
 * ### 왜 화면에서 떼어냈는가
 * **화면이 보여주는 조건과 읽어 주는 조건이 어긋나는 것**이 이 화면의 가장 쉬운 실수다.
 * 어느 줄은 Wi-Fi를 적고 어느 줄은 백그라운드를 적는데, 낭독만 다른 규칙을 쓰는 상태는
 * 눈으로 확인되지 않는다.
 *
 * 그 어긋남을 구조로 막았다. **보조 글자를 여기서 다시 고르지 않고
 * 화면이 적은 것([AppUsageRowTexts])을 그대로 받는다.** 두 벌의 조건문이 없으므로
 * 어긋날 자리 자체가 없다. 몫도 같은 이유로 다시 계산하지 않는다 — `<1%`처럼 다듬은 형태가 있다.
 *
 * 두 레이아웃([com.dksystems.tracephone.domain.model.AppListLayout])이 같은 문장을 쓴다.
 * 배치만 다르고 읽히는 값은 같으므로, 모양에 따라 낭독이 달라지면 오히려 혼란스럽다.
 */
internal fun describeAppUsageRow(
    usage: AppUsage,
    texts: AppUsageRowTexts,
    labels: AppUsageRowLabels
): String {
    val parts = listOfNotNull(
        labels.mobile.format(usage.app.label, ByteFormatter.format(usage.mobile.totalBytes)),
        // 화면에 보이는 '설치되지 않음'을 낭독만 빠뜨리면 어긋난다.
        // 몫·보조값보다 앞에 둔다. 맨 뒤로 보내면 보조값과 나란히 놓여
        // 어느 값에 걸리는 말인지 흐려진다.
        labels.notInstalled.takeIf { !usage.app.isInstalled },
        labels.share.format(texts.share),
        texts.secondary
    )

    return parts.joinToString(labels.separator)
}
