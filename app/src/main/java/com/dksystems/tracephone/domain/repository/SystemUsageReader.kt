package com.dksystems.tracephone.domain.repository

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.HourlyUsagePoint
import java.time.LocalDate

/**
 * 시스템에 직접 물어봐야 알 수 있는 것들.
 *
 * [UsageRepository]는 저장소에 쌓아 둔 것을 돌려주므로 **마지막 동기화 시점까지만** 안다.
 * 오늘치처럼 지금 값이 필요한 곳, 그리고 저장소에 아예 없는 것(앱 이름, 시간대별 분포)은 여기를 쓴다.
 *
 * 세 가지가 한 인터페이스에 있는 이유는 **저장소가 답할 수 없다**는 점이 같아서다.
 * 앞의 둘은 `NetworkStatsManager`에, 마지막 하나는 `PackageManager`에 묻는다.
 *
 * 대신 범위에 한계가 있다. 시스템은 상세 기록을 오래 보관하지 않으므로
 * **과거를 보려면 저장소를 써야 한다.**
 */
interface SystemUsageReader {

    /**
     * 오늘 지금까지의 앱별 사용량.
     *
     * 권한이 없거나 조회에 실패하면 **빈 목록**을 돌려준다. 예외를 던지지 않는 이유는
     * 이 값을 쓰는 쪽(경고 판정 · 한도 표시)이 한 번 못 읽었다고 멈출 일이 아니기 때문이다.
     */
    suspend fun readTodayUsage(): List<AppUsage>

    /**
     * 앱 하나의 하루를 시간대별로 나눈 값. 기록이 없는 시간대도 0으로 채워 **24개를 모두** 돌려준다.
     *
     * 빠뜨리면 그래프의 시간 축이 어긋나 언제 썼는지를 잘못 읽게 된다.
     */
    suspend fun readHourlyUsage(uid: Int, date: LocalDate): List<HourlyUsagePoint>

    /**
     * UID를 화면에 보여줄 이름으로 바꾼다.
     *
     * UID는 앱과 1:1이 아니다. 매핑에 실패해도 대체 라벨을 채워 **항상 표시 가능한 값**을 돌려준다.
     */
    suspend fun findAppIdentity(uid: Int): AppIdentity
}
