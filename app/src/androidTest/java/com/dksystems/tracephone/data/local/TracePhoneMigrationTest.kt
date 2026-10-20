package com.dksystems.tracephone.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 마이그레이션이 기존 기록을 그대로 보존하는지 검증한다.
 *
 * 이 DB의 누적 기록은 시스템이 통계를 지운 뒤에도 남는, 다시 만들 수 없는 자산이다.
 * 스키마를 바꿀 때 조용히 사라지는 일이 없어야 하므로 버전마다 이 검증을 남긴다.
 */
@RunWith(AndroidJUnit4::class)
class TracePhoneMigrationTest {

    private val databaseFile: File = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = TracePhoneDatabase::class,
        file = databaseFile,
        driver = AndroidSQLiteDriver()
    )

    /**
     * 테스트마다 빈 상태에서 시작한다.
     *
     * 앞선 테스트가 남긴 파일은 이미 새 버전으로 올라가 있어, 다음 테스트가 예전 버전으로
     * 만들려 할 때 '버전을 되돌릴 수 없다'는 오류가 난다.
     */
    @Before
    fun removeLeftoverDatabase() {
        listOf(databaseFile, journalFile("-wal"), journalFile("-shm")).forEach(File::delete)
    }

    private fun journalFile(suffix: String) = File(databaseFile.path + suffix)

    @Test
    fun 버전1의_기록은_버전2로_올린_뒤에도_그대로_남는다() {
        val usageDate = "2026-09-01"
        val uid = 10350
        val receivedBytes = 511_440_000L
        val transmittedBytes = 2_620_000L

        helper.createDatabase(version = 1).use { oldDatabase ->
            oldDatabase.execSQL(
                """
                INSERT INTO daily_app_usage
                    (usageDate, uid, networkKind, packageName,
                     receivedBytes, transmittedBytes, recordedAtEpochMillis)
                VALUES
                    ('$usageDate', $uid, 'MOBILE', 'com.example.app',
                     $receivedBytes, $transmittedBytes, 1)
                """
            )
        }

        helper.runMigrationsAndValidate(
            version = 2,
            migrations = TracePhoneMigrations.ALL.toList()
        ).use { migratedDatabase ->
            val statement = migratedDatabase.prepare(
                """
                SELECT receivedBytes, transmittedBytes,
                       foregroundReceivedBytes, foregroundTransmittedBytes
                FROM daily_app_usage
                WHERE usageDate = '$usageDate' AND uid = $uid AND networkKind = 'MOBILE'
                """
            )

            statement.use {
                assertTrue("옮긴 뒤에도 기존 행이 남아 있어야 한다", it.step())

                assertEquals(receivedBytes, it.getLong(0))
                assertEquals(transmittedBytes, it.getLong(1))

                // 예전 기록에는 구분 정보가 없다. 0이 아니라 null이어야 한다.
                // 0으로 채우면 화면에서 '전부 백그라운드'로 잘못 읽힌다.
                assertTrue("포그라운드 수신은 모름(null)이어야 한다", it.isNull(2))
                assertTrue("포그라운드 송신은 모름(null)이어야 한다", it.isNull(3))
            }
        }
    }

    @Test
    fun 버전2에서는_포그라운드_몫을_저장할_수_있다() {
        helper.createDatabase(version = 1).close()

        helper.runMigrationsAndValidate(
            version = 2,
            migrations = TracePhoneMigrations.ALL.toList()
        ).use { migratedDatabase ->
            migratedDatabase.execSQL(
                """
                INSERT INTO daily_app_usage
                    (usageDate, uid, networkKind, packageName,
                     receivedBytes, transmittedBytes,
                     foregroundReceivedBytes, foregroundTransmittedBytes,
                     recordedAtEpochMillis)
                VALUES
                    ('2026-09-02', 10001, 'MOBILE', 'com.example.app',
                     100, 20, 70, 10, 1)
                """
            )

            val statement = migratedDatabase.prepare(
                "SELECT foregroundReceivedBytes, foregroundTransmittedBytes FROM daily_app_usage"
            )
            statement.use {
                assertTrue(it.step())
                assertEquals(70L, it.getLong(0))
                assertEquals(10L, it.getLong(1))
            }
        }
    }
}
