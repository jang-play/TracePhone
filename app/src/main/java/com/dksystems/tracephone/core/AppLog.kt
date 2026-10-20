package com.dksystems.tracephone.core

import android.util.Log

/**
 * 앱 전역 로그 진입점.
 *
 * 태그와 형식을 한곳에서 통일한다. 출력 형식은 다음과 같다.
 * ```
 * [ERROR] NetworkStats query failed: date=2026-09-01, cause=SecurityException
 * [INFO ] Daily sync completed: days=7, records=1284
 * ```
 *
 * 에러 로그에는 원인, 발생 위치, 복구 방법이 드러나도록 메시지를 작성한다.
 */
object AppLog {

    private const val TAG = "TracePhone"

    private const val LEVEL_ERROR = "[ERROR]"
    private const val LEVEL_WARN = "[WARN ]"
    private const val LEVEL_INFO = "[INFO ]"
    private const val LEVEL_DEBUG = "[DEBUG]"

    fun error(message: String, cause: Throwable? = null) {
        Log.e(TAG, "$LEVEL_ERROR $message", cause)
    }

    fun warn(message: String) {
        Log.w(TAG, "$LEVEL_WARN $message")
    }

    fun info(message: String) {
        Log.i(TAG, "$LEVEL_INFO $message")
    }

    fun debug(message: String) {
        Log.d(TAG, "$LEVEL_DEBUG $message")
    }
}
