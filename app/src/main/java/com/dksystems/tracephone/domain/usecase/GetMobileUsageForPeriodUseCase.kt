package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import com.dksystems.tracephone.domain.repository.UsageRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 기간 내 모바일 총 사용량. **과거는 저장소에서, 오늘은 시스템에서 읽어 더한다.**
 *
 * 저장소는 6시간 주기로만 갱신되므로 오늘치를 저장소에서 읽으면 최대 6시간 뒤처진다.
 * 한도는 "지금 얼마나 썼는가"가 전부라 묵은 값이면 쓸모가 없다.
 *
 * 오늘을 두 번 세지 않도록 저장소 쪽 합계에서는 오늘을 **빼고** 센다.
 * 이 규칙이 한도 화면과 위젯 양쪽에서 똑같이 쓰이므로 한곳에 둔다.
 *
 * 저장소였다가 여기로 올라왔다. 저장 방식이 아니라 **업무 규칙**이기 때문이고,
 * 올라온 덕분에 규칙 자체를 단위 테스트로 고정할 수 있게 되었다.
 */
class GetMobileUsageForPeriodUseCase @Inject constructor(
    private val repository: UsageRepository,
    private val systemUsageReader: SystemUsageReader,
    private val clock: Clock
) {

    suspend operator fun invoke(period: UsagePeriod): Long {
        val today = LocalDate.now(clock)
        val includesToday = !today.isBefore(period.startDate) &&
            !today.isAfter(period.endDateInclusive)

        if (!includesToday) {
            return repository.readStoredMobileBytes(period)
        }

        val storedEndDate = today.minusDays(1)
        val storedBytes = if (storedEndDate.isBefore(period.startDate)) {
            0L
        } else {
            repository.readStoredMobileBytes(UsagePeriod(period.startDate, storedEndDate))
        }

        return storedBytes + readTodayMobileBytes(today)
    }

    /**
     * 오늘 모바일 사용량. 시스템을 먼저 읽고, 읽지 못하면 저장된 값으로 물러선다.
     *
     * 권한이 없을 때 0을 돌려주면 한도가 갑자기 줄어든 것처럼 보인다.
     */
    private suspend fun readTodayMobileBytes(today: LocalDate): Long {
        val fromSystem = systemUsageReader.readTodayUsage().sumOf { it.mobile.totalBytes }
        if (fromSystem > 0L) {
            return fromSystem
        }

        return repository.readStoredMobileBytes(UsagePeriod(today, today))
    }
}
