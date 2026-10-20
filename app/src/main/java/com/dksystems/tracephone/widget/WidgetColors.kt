package com.dksystems.tracephone.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.dksystems.tracephone.domain.model.WidgetAppearance
import com.dksystems.tracephone.domain.model.WidgetTextColor

/**
 * 설정에 따라 위젯에 쓸 색을 정한다.
 *
 * ### 왜 테마 색을 그대로 쓰지 않는가
 * 위젯은 배경화면 위에 놓인다. 기기 테마는 배경화면이 밝은지 어두운지 알지 못하므로,
 * 테마만 따르면 어두운 배경화면에 검은 글자가 묻히는 일이 생긴다.
 * 사용자가 고른 색을 그대로 쓰고, '자동'일 때만 테마를 따른다.
 *
 * ### 야간 모드를 직접 판단하는 이유
 * Glance의 `ColorProvider`는 색 하나 또는 색 리소스만 받는다.
 * 투명도를 적용하려면 실제 색 값이 필요한데, 리소스는 런타임에 알파를 바꿀 수 없다.
 * 그래서 야간 모드 여부를 읽어 우리가 직접 색을 고른다.
 *
 * ### 진행 막대는 고정색이다
 * 글자색을 따라 바뀌면 '넘겼는지 아닌지'를 색으로 알 수 없게 된다.
 * 어떤 배경화면 위에서도 읽히는 진한 파랑·빨강을 고정으로 쓴다.
 *
 * 앱 화면의 사용량 색을 끌어다 쓰지 않는 이유도 여기 있다. 앱은 테마를 알지만
 * 위젯은 **배경화면을 알지 못한다.** 흰 벽지와 검은 벽지 양쪽에서 읽히려면
 * 중간 밝기여야 하는데, 테마별로 갈린 앱 팔레트는 한쪽 끝에서 반드시 실패한다.
 * 값이 같아 보이더라도 별칭으로 묶으면 앱 색을 손볼 때 위젯이 이유 없이 깨진다.
 */
internal class WidgetColors(appearance: WidgetAppearance, isNightMode: Boolean) {

    /** '자동'일 때 쓸 바탕 글자색. 흰색·검정을 고른 경우에는 쓰이지 않는다. */
    private val autoTextColor = if (isNightMode) AUTO_TEXT_NIGHT else AUTO_TEXT_DAY

    private val baseTextColor: Color = when (appearance.textColor) {
        WidgetTextColor.AUTO -> autoTextColor
        WidgetTextColor.WHITE -> Color.White
        WidgetTextColor.BLACK -> Color.Black
    }

    val text: ColorProvider = ColorProvider(baseTextColor)

    /** 보조 문구. 같은 색을 옅게 써서 주된 숫자가 먼저 읽히게 한다. */
    val secondaryText: ColorProvider =
        ColorProvider(baseTextColor.copy(alpha = SECONDARY_TEXT_ALPHA))

    val background: ColorProvider = ColorProvider(
        (if (isNightMode) BACKGROUND_NIGHT else BACKGROUND_DAY)
            .copy(alpha = appearance.backgroundAlpha)
    )

    val progress: ColorProvider = ColorProvider(LIMIT_PROGRESS_COLOR)

    val progressExceeded: ColorProvider = ColorProvider(EXCEEDED_COLOR)

    /**
     * 막대의 빈 부분. 글자색을 옅게 깐다.
     *
     * 고정색으로 두면 배경을 투명하게 했을 때 배경화면과 겹쳐 막대의 끝이 어디인지 알 수 없다.
     */
    val progressTrack: ColorProvider = ColorProvider(baseTextColor.copy(alpha = TRACK_ALPHA))

    companion object {

        /** 기기가 야간 모드인지. 위젯은 앱 화면 밖에 있어 테마를 스스로 알지 못한다. */
        fun isNightMode(context: Context): Boolean {
            val nightFlag = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            return nightFlag == Configuration.UI_MODE_NIGHT_YES
        }

        private val AUTO_TEXT_DAY = Color(0xFF1B1B1F)
        private val AUTO_TEXT_NIGHT = Color(0xFFE3E2E6)

        private val BACKGROUND_DAY = Color(0xFFF2F3F7)
        private val BACKGROUND_NIGHT = Color(0xFF1C1F23)

        /** 한도 진행 막대. 'Wi-Fi'가 아니라 '한도 진행'이라는 뜻이다. */
        internal val LIMIT_PROGRESS_COLOR = Color(0xFF3A78E8)

        private val EXCEEDED_COLOR = Color(0xFFD93025)

        private const val SECONDARY_TEXT_ALPHA = 0.7f
        private const val TRACK_ALPHA = 0.25f
    }
}
