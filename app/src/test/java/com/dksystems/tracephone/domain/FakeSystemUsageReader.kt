package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.HourlyUsagePoint
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageStateSplit
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import java.time.LocalDate

/**
 * 테스트용 시스템 읽기.
 *
 * [FakeUsageRepository]와 나눠 둔 덕분에, 시스템 값만 필요한 테스트는
 * 저장소 쪽 값을 하나도 채우지 않아도 된다.
 */
class FakeSystemUsageReader(
    private val todayUsage: List<AppUsage> = emptyList(),
    private val hourlyUsage: List<HourlyUsagePoint> = emptyList()
) : SystemUsageReader {

    override suspend fun readTodayUsage(): List<AppUsage> = todayUsage

    override suspend fun readHourlyUsage(uid: Int, date: LocalDate): List<HourlyUsagePoint> =
        hourlyUsage

    override suspend fun findAppIdentity(uid: Int): AppIdentity =
        AppIdentity(uid = uid, packageName = null, label = "테스트 앱 $uid")

    companion object {

        /** 오늘 모바일 총량만 정해 두면 되는 테스트용. 앱 구분은 보지 않는다. */
        fun withTodayMobileBytes(bytes: Long) = FakeSystemUsageReader(
            todayUsage = listOf(
                AppUsage(
                    app = AppIdentity(uid = 1, packageName = null, label = "테스트 앱 1"),
                    usage = NetworkUsageSplit(
                        mobile = UsageStateSplit.allUnknown(totalBytes = bytes),
                        wifi = UsageStateSplit.ZERO
                    )
                )
            )
        )
    }
}
