package com.dksystems.tracephone.ui.component

import com.dksystems.tracephone.core.ChartAxis

/**
 * 막대 그래프가 밖과 주고받는 값들.
 *
 * 그리는 방법(캔버스·좌표)과 떼어 둔다. 이 파일은 **Compose 없이 읽히고 테스트된다.**
 * 화면을 만지지 않는 사람이 그래프에 무엇을 넘겨야 하는지 알려면 여기만 보면 된다.
 */

/**
 * 막대를 하나씩 스크린 리더에 내보내는 최대 개수.
 *
 * '전체 누적'은 저장소의 첫 기록일부터이므로 오래 쓰면 막대가 수백 개가 된다.
 * 그만큼 많으면 막대 하나가 1픽셀도 안 되어 **눈으로도 고를 수 없다.**
 * 전달되는 것이 추세뿐이므로 스크린 리더에도 요약만 준다.
 */
const val MAX_INDIVIDUALLY_LABELED_BARS = 31

/** 막대 하나가 나타내는 값. 날짜든 시간대든 그리는 방식은 같다. */
data class UsageBarValue(
    val mobileBytes: Long,
    val wifiBytes: Long
) {
    val totalBytes: Long
        get() = mobileBytes + wifiBytes
}

/**
 * 가로축에 적을 라벨. [index]번째 막대 아래에 [text]를 적고 그 자리에 세로 격자선을 긋는다.
 *
 * 막대마다 적으면 글자가 겹쳐 아무것도 읽을 수 없고,
 * 양 끝에만 적으면 가운데 막대가 몇 시인지 세어야 한다.
 */
data class BarAxisLabel(val index: Int, val text: String)

/**
 * 그래프의 글자들. 눈에 보이는 축 라벨과 스크린 리더만 읽는 막대 설명을 함께 둔다.
 *
 * 축 라벨을 픽셀에 직접 그리는 탓에 그 글자는 접근성 트리에 없다.
 * 두 가지를 같은 자리에서 받으면 한쪽만 채우고 넘어가기 어렵다.
 *
 * @property describeBar [index]번째 막대를 스크린 리더가 읽을 한 줄
 */
data class BarChartLabels(
    val xAxis: List<BarAxisLabel>,
    val describeBar: (index: Int, bar: UsageBarValue) -> String
)

/**
 * 막대를 눌렀을 때 무슨 일이 벌어지는가.
 *
 * 두 쓰임을 한 타입으로 두면 스크린 리더가 잘못 읽는다.
 * 눌러서 넘어가 버리는 그래프에서 "선택 안 됨"을 서른 번 읽어 주는 일이 그렇다.
 *
 * @property actionLabel 스크린 리더가 "두 번 탭하면 ~"으로 읽어 줄 동작 이름
 */
sealed interface BarInteraction {

    val actionLabel: String
    val onSelect: (index: Int) -> Unit

    /** 누르면 다른 화면으로 넘어간다. 이 그래프에는 고른 흔적이 남지 않는다. */
    data class Navigate(
        override val actionLabel: String,
        override val onSelect: (index: Int) -> Unit
    ) : BarInteraction

    /** 누른 막대가 골라진 채로 남고, 화면의 다른 곳이 그 값을 보여준다. */
    data class Select(
        val selectedIndex: Int?,
        override val actionLabel: String,
        override val onSelect: (index: Int) -> Unit
    ) : BarInteraction
}

/**
 * 그래프 세로축의 맨 위 값.
 *
 * 그래프가 스스로 고르는 값이라 밖에서는 알 수 없는데,
 * 스크린 리더에 읽어 줄 요약에는 이 값이 필요하다. 두 곳이 같은 계산을 쓰게 한다.
 */
fun barChartAxisMaxBytes(bars: List<UsageBarValue>): Long {
    if (bars.isEmpty()) {
        return 0L
    }

    return ChartAxis.forMaxValue(bars.maxOf { it.totalBytes }).maxValue
}
