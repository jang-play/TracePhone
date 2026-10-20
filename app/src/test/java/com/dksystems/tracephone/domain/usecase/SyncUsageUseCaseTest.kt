package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.core.SyncConstants
import com.dksystems.tracephone.domain.FakeSyncStatusPreferences
import com.dksystems.tracephone.domain.FakeUsageRepository
import com.dksystems.tracephone.domain.FakeUsageSynchronizer
import com.dksystems.tracephone.domain.model.UsageSyncResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SyncUsageUseCaseTest {

    private val now = Instant.parse("2026-10-02T02:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"))

    private fun useCase(
        repository: FakeUsageRepository,
        synchronizer: FakeUsageSynchronizer,
        syncStatus: FakeSyncStatusPreferences = FakeSyncStatusPreferences()
    ) = SyncUsageUseCase(repository, synchronizer, syncStatus, clock)

    @Test
    fun `저장된 기록이 없으면 긴 백필을 수행한다`() = runTest {
        val repository = FakeUsageRepository(hasStoredUsage = false)
        val synchronizer = FakeUsageSynchronizer()

        useCase(repository, synchronizer).invoke()

        assertEquals(SyncConstants.INITIAL_BACKFILL_DAYS, synchronizer.lastRequestedSyncDayCount)
    }

    @Test
    fun `기록이 있으면 최근 며칠만 다시 훑는다`() = runTest {
        val repository = FakeUsageRepository(hasStoredUsage = true)
        val synchronizer = FakeUsageSynchronizer()

        useCase(repository, synchronizer).invoke()

        assertEquals(SyncConstants.RECENT_BACKFILL_DAYS, synchronizer.lastRequestedSyncDayCount)
    }

    @Test
    fun `포그라운드 구분이 기록된 적이 없으면 한 번은 길게 훑는다`() = runTest {
        // 이 구분을 도입하기 전부터 쓰던 기기. 시스템이 아직 보관 중인 기간을 채워야 한다.
        val repository = FakeUsageRepository(hasStoredUsage = true, hasStateRecordedUsage = false)
        val synchronizer = FakeUsageSynchronizer()

        useCase(repository, synchronizer).invoke()

        assertEquals(SyncConstants.INITIAL_BACKFILL_DAYS, synchronizer.lastRequestedSyncDayCount)
    }

    @Test
    fun `포그라운드 구분이 한 번 기록되면 다시 길게 훑지 않는다`() = runTest {
        val repository = FakeUsageRepository(hasStoredUsage = true, hasStateRecordedUsage = true)
        val synchronizer = FakeUsageSynchronizer()

        useCase(repository, synchronizer).invoke()

        assertEquals(SyncConstants.RECENT_BACKFILL_DAYS, synchronizer.lastRequestedSyncDayCount)
    }

    @Test
    fun `강제 백필을 요청하면 기록이 있어도 긴 백필을 수행한다`() = runTest {
        val repository = FakeUsageRepository(hasStoredUsage = true)
        val synchronizer = FakeUsageSynchronizer()

        useCase(repository, synchronizer).invoke(forceInitialBackfill = true)

        assertEquals(SyncConstants.INITIAL_BACKFILL_DAYS, synchronizer.lastRequestedSyncDayCount)
    }

    @Test
    fun `하루라도 읽어 냈으면 확인한 시각을 남긴다`() = runTest {
        val syncStatus = FakeSyncStatusPreferences()
        val synchronizer = FakeUsageSynchronizer(
            UsageSyncResult.Success(syncedDayCount = 3, recordCount = 120)
        )

        useCase(FakeUsageRepository(), synchronizer, syncStatus).invoke()

        assertEquals(1, syncStatus.recordCount)
        assertEquals(now.toEpochMilli(), syncStatus.lastRecorded)
    }

    /**
     * 데이터를 한 바이트도 쓰지 않은 날에는 저장할 레코드가 없다. 그래도 **확인은 일어났다.**
     * 이것을 '확인 못 함'으로 적으면 멀쩡한 앱이 멈춘 것처럼 보인다.
     */
    @Test
    fun `저장할 레코드가 없어도 읽어 냈으면 남긴다`() = runTest {
        val syncStatus = FakeSyncStatusPreferences()
        val synchronizer = FakeUsageSynchronizer(
            UsageSyncResult.Success(syncedDayCount = 7, recordCount = 0)
        )

        useCase(FakeUsageRepository(), synchronizer, syncStatus).invoke()

        assertEquals(1, syncStatus.recordCount)
    }

    /**
     * 모든 날짜가 조회에 실패해 건너뛰어져도 `syncedDayCount = 0`인 **성공**이 돌아온다.
     * 성공 여부만 보면 아무것도 읽지 못한 동기화가 "방금 확인함"을 적게 된다.
     */
    @Test
    fun `하루도 읽어 내지 못했으면 남기지 않는다`() = runTest {
        val syncStatus = FakeSyncStatusPreferences()
        val synchronizer = FakeUsageSynchronizer(
            UsageSyncResult.Success(syncedDayCount = 0, recordCount = 0)
        )

        useCase(FakeUsageRepository(), synchronizer, syncStatus).invoke()

        assertEquals(0, syncStatus.recordCount)
    }

    @Test
    fun `권한이 없으면 남기지 않는다`() = runTest {
        val syncStatus = FakeSyncStatusPreferences()
        val synchronizer = FakeUsageSynchronizer(UsageSyncResult.PermissionRequired)

        useCase(FakeUsageRepository(), synchronizer, syncStatus).invoke()

        assertEquals(0, syncStatus.recordCount)
    }

    @Test
    fun `실패했으면 남기지 않는다`() = runTest {
        val syncStatus = FakeSyncStatusPreferences()
        val synchronizer = FakeUsageSynchronizer(
            UsageSyncResult.Failure(IllegalStateException("조회 실패"))
        )

        useCase(FakeUsageRepository(), synchronizer, syncStatus).invoke()

        assertEquals(0, syncStatus.recordCount)
    }
}
