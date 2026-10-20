package com.dksystems.tracephone.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 회선 종류를 구분하는 색.
 *
 * 그래프·막대·범례 어디에서나 같은 의미로 쓴다.
 * 화면마다 다른 색을 쓰면 사용자가 매번 범례를 다시 읽어야 한다.
 */
@Immutable
data class NetworkUsageColors(
    val mobile: Color,
    val wifi: Color
)

/**
 * 화면을 보고 있을 때와 그렇지 않을 때를 구분하는 색.
 *
 * 회선 색과 겹치지 않는 계열을 골랐다. 같은 화면에 두 종류의 막대가 함께 놓이므로
 * 색이 비슷하면 무엇을 나눈 막대인지 헷갈린다.
 *
 * @property unknown 구분 정보가 없는 몫. '모른다'는 표시이므로 무채색을 쓴다.
 */
@Immutable
data class UsageStateColors(
    val foreground: Color,
    val background: Color,
    val unknown: Color
)

/**
 * 사용량을 뜻하는 색 묶음.
 *
 * 회선과 상태를 나눠 담는다. 둘은 별개의 구분이고,
 * 그래프처럼 회선 색만 필요한 곳에 상태 색까지 딸려 가지 않게 한다.
 */
@Immutable
data class UsageColors(
    val network: NetworkUsageColors,
    val state: UsageStateColors
)

/**
 * 지금 테마에 맞는 사용량 색.
 *
 * `MaterialTheme.colorScheme`과 같은 모양으로 읽히도록 이름을 맞췄다.
 */
object UsageTheme {

    val colors: UsageColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUsageColors.current
}

/**
 * 기본값을 라이트로 둔다.
 *
 * 테마 밖에서 그려도 죽지 않아야 하고, `static`이라 테마가 바뀌면
 * 이 값을 읽는 화면 전체가 다시 그려진다. 테마 전환은 정확히 그 동작이 맞다.
 */
internal val LocalUsageColors = staticCompositionLocalOf { LightUsageColors }

// 원본 색을 OKLab으로 옮겨 색상(hue)과 채도는 그대로 두고 명도만 옮긴 값들이다.
// 색상 이동은 최대 2도라 '따뜻한 색 = 모바일, 차가운 색 = Wi-Fi'라는 구분이 유지된다.
// 목표 휘도는 카드 배경 기준 4.5:1이 나오는 지점(라이트 0.125, 다크 0.40)이다.

private val LightMobileUsage = Color(0xFFA04B00)
private val LightWifiUsage = Color(0xFF1F5FCC)
private val LightForegroundUsage = Color(0xFF00713F)
private val LightBackgroundUsage = Color(0xFF7A4BB5)
private val LightUnknownStateUsage = Color(0xFF5E6469)

private val DarkMobileUsage = Color(0xFFF5904A)
private val DarkWifiUsage = Color(0xFF7AABFF)
private val DarkForegroundUsage = Color(0xFF52BD82)
private val DarkBackgroundUsage = Color(0xFFC095FF)
private val DarkUnknownStateUsage = Color(0xFFA4AAB1)

/**
 * ### 왜 라이트와 다크를 나누는가
 *
 * 이 색들은 대부분 `Card` 위에 놓인다. Material3의 카드 바탕은 `surfaceContainerHighest`이고,
 * [TracePhoneTheme]이 그 토큰을 지정하지 않아 M3 기본값이 그대로 쓰인다
 * (라이트 `#E6E0E9`, 다크 `#36343B`).
 *
 * 비텍스트 대비 3:1을 맞추려면 라이트 카드 위에서는 색 휘도가 0.207 **이하**여야 하고
 * 다크 카드 위에서는 0.207 **이상**이어야 한다. 두 구간이 한 점에서 겨우 스치므로
 * **하나의 값으로 양쪽을 만족시킬 수 없다.** 나누는 것 말고는 방법이 없다.
 *
 * ### 왜 `MaterialTheme.colorScheme`에서 가져오지 않는가
 *
 * 의미를 가진 색이다. 동적 색상을 따르면 사용자의 배경화면에 따라
 * 모바일이 파랗게 보이는 일이 생긴다.
 *
 * ### 다섯 색의 밝기가 서로 비슷한 이유
 *
 * 배경과의 대비를 우선했기 때문이다. 그 대가로 색끼리의 대비가 거의 없어지므로
 * 맞붙여 그리는 막대는 칸 사이를 띄운다([SegmentedUsageBar] 참고).
 */
internal val LightUsageColors = UsageColors(
    network = NetworkUsageColors(mobile = LightMobileUsage, wifi = LightWifiUsage),
    state = UsageStateColors(
        foreground = LightForegroundUsage,
        background = LightBackgroundUsage,
        unknown = LightUnknownStateUsage
    )
)

internal val DarkUsageColors = UsageColors(
    network = NetworkUsageColors(mobile = DarkMobileUsage, wifi = DarkWifiUsage),
    state = UsageStateColors(
        foreground = DarkForegroundUsage,
        background = DarkBackgroundUsage,
        unknown = DarkUnknownStateUsage
    )
)
