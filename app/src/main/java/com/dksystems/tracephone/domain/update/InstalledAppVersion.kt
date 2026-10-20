package com.dksystems.tracephone.domain.update

/**
 * 지금 설치돼 돌고 있는 앱의 버전.
 *
 * `BuildConfig`를 직접 읽지 않고 인터페이스로 받는다. `BuildConfig`는 생성되는 클래스라
 * JVM 단위 테스트에서 값을 바꿔 끼울 수 없고, 그러면 버전 비교 자체를 테스트할 수 없다.
 */
interface InstalledAppVersion {
    val versionCode: Int
    val versionName: String
}
