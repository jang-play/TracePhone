package com.dksystems.tracephone.data.system

import com.dksystems.tracephone.domain.model.NetworkKind
import com.dksystems.tracephone.domain.model.UsageBytes

/**
 * 시스템에서 막 읽어온, 아직 앱 이름이 붙지 않은 사용량 한 줄.
 *
 * 하루 · UID · 회선 종류 조합 하나에 대응한다.
 *
 * @property uid 커널 UID. 특수 UID(테더링, 제거된 앱 등)가 섞여 있을 수 있다.
 * @property bytes 해당 조합의 총 사용량
 * @property foregroundBytes 그중 화면을 보고 있을 때 쓴 몫.
 *   시스템이 포그라운드 여부를 알려주지 않은 경우 null이다. 0과 구분해야 하므로 null을 쓴다.
 */
data class RawUsageRecord(
    val uid: Int,
    val networkKind: NetworkKind,
    val bytes: UsageBytes,
    val foregroundBytes: UsageBytes?
)
