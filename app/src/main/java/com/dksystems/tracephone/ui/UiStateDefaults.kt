package com.dksystems.tracephone.ui

/**
 * 화면이 잠깐 사라져도 흐름을 끊지 않고 기다리는 시간.
 *
 * 화면 회전이나 다른 앱을 잠시 들렀다 오는 정도로 구독을 끊으면, 돌아올 때마다 DB와 시스템을
 * 다시 조회해 빈 화면이 한 번 스친다. 반대로 무한정 붙들면 화면이 없는 동안에도 계산이 이어진다.
 *
 * **모든 ViewModel이 같은 값을 쓴다.** 화면마다 다르면 어떤 화면은 돌아올 때 깜빡이고
 * 어떤 화면은 안 깜빡이는 이유를 설명할 수 없다.
 */
internal const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
