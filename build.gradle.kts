// 루트 빌드 스크립트. 플러그인 버전만 선언하고 적용은 각 모듈에서 한다.
//
// AGP 9부터 Kotlin 지원이 내장되어 org.jetbrains.kotlin.android 플러그인을 선언하지 않는다.
// Compose 컴파일러 플러그인은 여전히 별도로 필요하다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
