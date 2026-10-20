package com.dksystems.tracephone.domain.model

/**
 * 데이터가 오간 회선 종류.
 *
 * 이 앱의 존재 이유가 "모바일이었나 Wi-Fi였나"를 구분하는 것이므로
 * 사용량을 다루는 모든 지점에서 이 값이 함께 따라다닌다.
 */
enum class NetworkKind {
    MOBILE,
    WIFI
}
