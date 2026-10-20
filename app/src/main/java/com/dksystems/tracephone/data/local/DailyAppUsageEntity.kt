package com.dksystems.tracephone.data.local

import androidx.room.Entity
import androidx.room.Index
import com.dksystems.tracephone.domain.model.NetworkKind
import java.time.LocalDate

/**
 * 하루 · 앱 · 회선 종류 조합 하나의 사용량.
 *
 * ### 복합 기본키를 쓰는 이유
 * 시스템이 돌려주는 값은 **그 날의 누적치**이지 마지막 조회 이후의 증분이 아니다.
 * 같은 날을 여러 번 동기화해도 항상 최신 값으로 덮어써야 한다.
 *
 * `(usageDate, uid, networkKind)`를 기본키로 두고 `REPLACE`로 저장하면
 * 실수로 값을 누적시키는 것이 구조적으로 불가능해진다.
 *
 * ### 포그라운드 몫을 열로 더한 이유
 * 기본키에 상태를 넣으면 마이그레이션으로 생긴 '구분 없음' 행과 새로 들어오는 상태별 행이
 * 공존해 같은 날을 두 번 세게 된다. 이를 막으려면 날짜별 삭제 후 삽입이 필요한데,
 * 시스템 보관 기간이 지난 과거를 동기화할 때 조회 결과가 비어 아카이브를 지워버릴 수 있다.
 * 기본키를 그대로 두고 열만 더하면 위 덮어쓰기 규칙이 그대로 유지된다.
 *
 * @property packageName 저장 시점의 대표 패키지명. 앱이 나중에 제거되어도 이름을 보여주기 위해 함께 남긴다.
 * @property foregroundReceivedBytes [receivedBytes] 가운데 화면을 보고 있을 때 쓴 몫.
 *   **null은 0이 아니라 '모름'이다.** 이 구분을 기록하기 전에 쌓인 행이 여기에 해당한다.
 *   0으로 채우면 과거 기록이 전부 백그라운드였던 것처럼 보인다.
 * @property foregroundTransmittedBytes [transmittedBytes] 가운데 화면을 보고 있을 때 쓴 몫. null 의미는 위와 같다.
 * @property recordedAtEpochMillis 이 행을 마지막으로 갱신한 시각. 동기화 동작을 진단할 때 쓴다.
 */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["usageDate", "uid", "networkKind"],
    indices = [Index("usageDate"), Index("uid")]
)
data class DailyAppUsageEntity(
    val usageDate: LocalDate,
    val uid: Int,
    val networkKind: NetworkKind,
    val packageName: String?,
    val receivedBytes: Long,
    val transmittedBytes: Long,
    val foregroundReceivedBytes: Long?,
    val foregroundTransmittedBytes: Long?,
    val recordedAtEpochMillis: Long
)
