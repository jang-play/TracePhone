package com.dksystems.tracephone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 기본 타이포그래피에서 숫자 표시에 쓰는 스타일만 조정한다.
 *
 * 사용량 수치는 목록에서 세로로 나열되므로 고정폭 글꼴이라야 자릿수가 눈에 잘 들어온다.
 */
val UsageValueTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp
)

/**
 * 합계에 딸린 모바일/Wi-Fi 값처럼 보조적인 수치에 쓴다.
 *
 * 같은 고정폭 글꼴을 유지하되 크기를 줄여, 어느 것이 합계인지 글자 크기만으로 구분되게 한다.
 */
val UsageSubValueTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp
)

val TracePhoneTypography = Typography()
