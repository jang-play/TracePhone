package com.dksystems.tracephone.domain.appearance

import com.dksystems.tracephone.domain.model.ThemeMode

/**
 * 고른 화면 모드를 시스템에 알린다.
 *
 * ### 왜 저장과 따로 두는가
 * `themes.xml`이 정하는 **시작 화면은 리소스 한정자**(`values-night`)라 런타임에 바꿀 수 없다.
 * 저장만 하면 어둡게로 둔 사용자가 앱을 열 때마다 흰 화면을 먼저 보고 그 다음 어두운 화면을 본다.
 * 시스템에 알리면 앱의 구성이 바뀌어 시작 화면까지 맞는다.
 *
 * 저장하는 일과 시스템에 알리는 일은 **성격이 다르다.** 하나는 값을 남기는 것이고
 * 하나는 화면을 다시 그리게 하는 것이라, 뒤엣것은 액티비티 재생성까지 부른다.
 * 저장소 안에 숨겨 두면 그 무게가 보이지 않으므로 이 앱의 다른 설정들과 같이
 * **누르는 자리(ViewModel)에서 순서대로 부른다** — 위젯 설정이 저장 뒤에 다시 그리기를 부르는 것과 같다.
 */
interface ThemeModeApplier {

    fun apply(mode: ThemeMode)
}
