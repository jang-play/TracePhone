package com.dksystems.tracephone.core

import android.content.Context
import android.os.PowerManager

/**
 * 배터리 최적화 예외로 등록되어 있는지.
 *
 * 예외가 아니면 시스템이 앱을 App Standby Bucket으로 강등시키고, 등급마다 정해진
 * **실행 횟수 상한**이 걸린다. S23+에서 읽은 실제 설정으로는 WORKING_SET이 4시간에 10회,
 * FREQUENT가 12시간에 8회, RARE가 24시간에 3회다.
 *
 * 그래서 이 값이 "경고를 얼마나 자주 확인할 수 있는가"를 그대로 결정한다.
 * 설정 화면은 이 값을 사용자에게 보여주고, [com.dksystems.tracephone.work.UsageSyncScheduler]는
 * 이 값으로 확인 간격을 고른다. 두 곳이 같은 판단을 쓰도록 여기 하나만 둔다.
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
