package com.dksystems.tracephone.domain.model

/**
 * 위젯 글자색.
 *
 * 위젯은 배경화면 위에 놓이므로 기기 테마만으로는 대비를 보장할 수 없다.
 * 어두운 배경화면에 밝은 테마를 쓰면 검은 글자가 묻힌다.
 * 그래서 테마를 따르는 [AUTO] 외에 직접 고르는 선택지를 둔다.
 */
enum class WidgetTextColor {
    /** 기기 테마를 따른다. 밝은 테마면 어두운 글자, 어두운 테마면 밝은 글자. */
    AUTO,
    WHITE,
    BLACK
}

/**
 * 위젯의 겉모습 설정.
 *
 * @property backgroundTransparencyPercent 배경이 얼마나 비치는지. 0이면 불투명, 100이면 배경 없음.
 *   '투명도'라는 이름 그대로 저장한다. 화면에서는 투명도로 말하고 코드에서는 불투명도로 계산하면
 *   어느 쪽이 100인지 매번 되짚어야 한다.
 */
data class WidgetAppearance(
    val textColor: WidgetTextColor,
    val backgroundTransparencyPercent: Int
) {

    /** 배경에 실제로 적용할 불투명도. 0.0(완전 투명) ~ 1.0(불투명). */
    val backgroundAlpha: Float
        get() {
            val transparency = backgroundTransparencyPercent
                .coerceIn(MIN_TRANSPARENCY_PERCENT, MAX_TRANSPARENCY_PERCENT)
            return (MAX_TRANSPARENCY_PERCENT - transparency).toFloat() / MAX_TRANSPARENCY_PERCENT
        }

    companion object {
        const val MIN_TRANSPARENCY_PERCENT = 0
        const val MAX_TRANSPARENCY_PERCENT = 100

        /** 슬라이더가 멈추는 간격. 1%씩 움직이면 저장이 잦아지고 손으로 맞추기도 어렵다. */
        const val TRANSPARENCY_STEP_PERCENT = 5

        /** 기본값은 지금까지의 모습 그대로다. 설정을 건드리지 않은 사람의 위젯이 바뀌면 안 된다. */
        val DEFAULT = WidgetAppearance(
            textColor = WidgetTextColor.AUTO,
            backgroundTransparencyPercent = MIN_TRANSPARENCY_PERCENT
        )
    }
}
