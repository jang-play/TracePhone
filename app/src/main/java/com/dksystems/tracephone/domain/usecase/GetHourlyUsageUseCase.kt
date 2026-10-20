package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.HourlyUsagePoint
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import java.time.LocalDate
import javax.inject.Inject

/**
 * 앱 하나의 하루를 시간대별로 구한다.
 */
class GetHourlyUsageUseCase @Inject constructor(
    private val systemUsageReader: SystemUsageReader
) {

    suspend operator fun invoke(uid: Int, date: LocalDate): List<HourlyUsagePoint> =
        systemUsageReader.readHourlyUsage(uid, date)
}
