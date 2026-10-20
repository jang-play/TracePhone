package com.dksystems.tracephone.data.system

import android.app.usage.NetworkStats

/**
 * 전체 합계를 나타내는 특수 UID.
 *
 * `NetworkStatsManager`는 개별 앱 줄과 **전체 합계 줄을 같은 목록에 섞어** 돌려준다.
 * 걸러내지 않고 함께 더하면 총량이 정확히 두 배가 된다.
 *
 * 시스템 통계를 읽는 곳이면 어디든 이 값을 걸러야 하므로 한곳에 둔다.
 */
internal val AGGREGATE_UID = NetworkStats.Bucket.UID_ALL
