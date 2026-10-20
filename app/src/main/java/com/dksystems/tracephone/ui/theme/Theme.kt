package com.dksystems.tracephone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface
)

/**
 * 앱 전역 테마.
 *
 * Android 12 이상에서는 사용자의 배경화면 색을 따르는 동적 색상을 사용한다.
 *
 * 사용량 색([UsageColors])은 의미를 가진 색이라 동적 색상에는 휩쓸리지 않되,
 * **명암은 따라간다.** 같은 값으로 밝은 배경과 어두운 배경 양쪽에서
 * 읽히게 할 방법이 없기 때문이다. 이유는 [LightUsageColors]에 적어 두었다.
 *
 * 팔레트는 `colorScheme`이 아니라 [useDarkTheme]을 보고 고른다.
 * 동적 색상 경로를 타든 말든 사용량 색은 흔들리지 않아야 한다.
 */
@Composable
fun TracePhoneTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        supportsDynamicColor && useDarkTheme -> dynamicDarkColorScheme(context)
        supportsDynamicColor -> dynamicLightColorScheme(context)
        useDarkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalUsageColors provides if (useDarkTheme) DarkUsageColors else LightUsageColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TracePhoneTypography,
            content = content
        )
    }
}
