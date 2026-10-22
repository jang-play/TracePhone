package com.dksystems.tracephone.data.backup

import com.dksystems.tracephone.data.local.DailyAppUsageEntity
import com.dksystems.tracephone.domain.model.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * 백업 파일의 해석을 고정한다.
 *
 * **여기가 신뢰 경계다.** 사용자가 고른 파일이 그대로 누적 DB에 들어가므로, 조용히 메우거나
 * 건너뛰면 복구할 수 없는 기록이 조용히 틀어진다. 틀린 입력은 전부 거부해야 한다.
 */
class UsageCsvTest {

    private val entity = DailyAppUsageEntity(
        usageDate = LocalDate.of(2026, 10, 22),
        uid = 10234,
        networkKind = NetworkKind.MOBILE,
        packageName = "com.example.app",
        receivedBytes = 1_234_567L,
        transmittedBytes = 89_012L,
        foregroundReceivedBytes = 1_000_000L,
        foregroundTransmittedBytes = 50_000L,
        recordedAtEpochMillis = 1_793_000_000_000L
    )

    private fun roundTrip(source: DailyAppUsageEntity): DailyAppUsageEntity {
        val lines = sequenceOf(USAGE_CSV_HEADER, usageCsvLineOf(source))
        return parseUsageCsv(lines).single()
    }

    @Test
    fun `행 하나가 그대로 왕복한다`() {
        assertEquals(entity, roundTrip(entity))
    }

    /**
     * **이 프로젝트에서 가장 중요한 구분이다.** null은 0이 아니라 '모름'이다.
     * 0으로 되살리면 포그라운드 구분 이전의 기록이 전부 백그라운드였던 것처럼 보인다.
     */
    @Test
    fun `포그라운드 모름이 0으로 바뀌지 않는다`() {
        val unknownState = entity.copy(
            foregroundReceivedBytes = null,
            foregroundTransmittedBytes = null
        )

        val restored = roundTrip(unknownState)

        assertNull(restored.foregroundReceivedBytes)
        assertNull(restored.foregroundTransmittedBytes)
    }

    @Test
    fun `지워진 앱의 빈 패키지명이 왕복한다`() {
        assertNull(roundTrip(entity.copy(packageName = null)).packageName)
    }

    @Test
    fun `0바이트와 음수 uid가 왕복한다`() {
        val edge = entity.copy(uid = -1, receivedBytes = 0L, transmittedBytes = 0L)

        assertEquals(edge, roundTrip(edge))
    }

    @Test
    fun `여러 줄을 모두 읽는다`() {
        val second = entity.copy(uid = 10235, networkKind = NetworkKind.WIFI)
        val lines = sequenceOf(
            USAGE_CSV_HEADER,
            usageCsvLineOf(entity),
            usageCsvLineOf(second)
        )

        assertEquals(listOf(entity, second), parseUsageCsv(lines))
    }

    /** 파일 끝의 줄바꿈이 남기는 빈 줄은 오류가 아니다. */
    @Test
    fun `끝의 빈 줄을 무시한다`() {
        val lines = sequenceOf(USAGE_CSV_HEADER, usageCsvLineOf(entity), "", "   ")

        assertEquals(1, parseUsageCsv(lines).size)
    }

    @Test
    fun `다른 파일을 고르면 거부한다`() {
        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf("name,age", "a,1"))
        }
    }

    /**
     * 바이너리 파일을 고르면 첫 '줄'이 수십만 바이트가 된다. 그것이 그대로 메시지에
     * 들어가면 로그가 그만큼 불어난다. 앞부분만 적는지 고정한다.
     */
    @Test
    fun `아주 긴 머리글을 메시지에 그대로 담지 않는다`() {
        val binaryLikeLine = "x".repeat(500_000)

        val error = assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(binaryLikeLine))
        }

        assertEquals(true, (error.message?.length ?: 0) < 200)
    }

    @Test
    fun `빈 파일을 거부한다`() {
        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(emptySequence())
        }
    }

    @Test
    fun `머리글만 있으면 거부한다`() {
        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER))
        }
    }

    /** 패키지명에 쉼표가 섞이면 칸 수가 어긋난다. 따옴표를 쓰지 않는 대신 이것으로 막는다. */
    @Test
    fun `칸 수가 다른 줄을 거부한다`() {
        val tooMany = usageCsvLineOf(entity) + ",extra"

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, tooMany))
        }
    }

    @Test
    fun `칸이 모자란 줄을 거부한다`() {
        val missingLast = usageCsvLineOf(entity).substringBeforeLast(",")

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, missingLast))
        }
    }

    /**
     * 마지막 칸만 비어 있는 줄이다. 칸은 9개지만 `recordedAtEpochMillis`가 없다.
     *
     * **자바의 `split`은 끝의 빈 칸을 버린다.** 코틀린 것은 버리지 않아 9개로 세어지고
     * 숫자 검사에서 걸린다. 이 테스트가 그 차이에 기대고 있다는 사실을 고정한다.
     */
    @Test
    fun `마지막 칸이 비어 있으면 거부한다`() {
        val emptyLast = usageCsvLineOf(entity).substringBeforeLast(",") + ","

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, emptyLast))
        }
    }

    @Test
    fun `날짜가 아니면 거부한다`() {
        val badDate = usageCsvLineOf(entity).replaceFirst("2026-10-22", "2026-13-99")

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, badDate))
        }
    }

    /** 회선 종류는 기본키의 일부다. 모르는 값을 넣으면 어느 회선인지 알 수 없는 행이 생긴다. */
    @Test
    fun `모르는 회선 종류를 거부한다`() {
        val badKind = usageCsvLineOf(entity).replaceFirst("MOBILE", "SATELLITE")

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, badKind))
        }
    }

    @Test
    fun `숫자가 아닌 바이트 수를 거부한다`() {
        val badBytes = usageCsvLineOf(entity).replaceFirst("1234567", "많음")

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, badBytes))
        }
    }

    /** 값이 있는데 숫자가 아닌 것과, 아예 비어 있는 '모름'을 뭉개지 않는다. */
    @Test
    fun `포그라운드 칸에 숫자가 아닌 값이 있으면 거부한다`() {
        val badForeground = usageCsvLineOf(entity).replaceFirst("1000000", "모름")

        assertThrows(UsageCsvFormatException::class.java) {
            parseUsageCsv(sequenceOf(USAGE_CSV_HEADER, badForeground))
        }
    }
}
