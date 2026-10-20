package com.dksystems.tracephone.domain.permission

/**
 * 사용 정보 접근(`PACKAGE_USAGE_STATS`) 허용 여부를 알려준다.
 *
 * 이 권한은 일반 런타임 권한이 아니라 앱이 요청할 수 없다.
 * 사용자가 시스템 설정 화면에서 직접 켜야 하므로, 앱은 "지금 허용되어 있는지"만 확인할 수 있다.
 *
 * 인터페이스로 분리해 두면 도메인과 화면 로직을 실제 시스템 없이 테스트할 수 있다.
 */
fun interface UsageAccessPermission {

    /** 현재 사용 정보 접근이 허용되어 있으면 true. */
    fun isGranted(): Boolean
}
