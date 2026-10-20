package com.dksystems.tracephone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "마지막으로 확인한 지 얼마나 됐는가"의 경계를 고정한다.
 *
 * 이 문구는 **앱이 조용히 멈췄는지 알리는 유일한 신호**다. 틀려도 화면은 멀쩡하고,
 * 사용자는 멈춘 앱을 정상으로 믿게 된다.
 */
class SyncAgeTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-10-02T02:00:00Z")
    private val clock = Clock.fixed(now, zone)

    /** [ago]만큼 과거의 시각. 음수를 주면 미래가 된다. */
    private fun at(ago: Duration): Long = now.minus(ago).toEpochMilli()

    private fun ageOf(ago: Duration): SyncAge = syncAgeOf(at(ago), clock)

    @Test
    fun `확인한 적이 없으면 아무 시각도 없다`() {
        assertEquals(SyncAge.NeverChecked, syncAgeOf(null, clock))
    }

    @Test
    fun `방금 확인했으면 방금이다`() {
        assertEquals(SyncAge.JustNow, ageOf(Duration.ZERO))
    }

    @Test
    fun `1분이 되면 분으로 넘어간다`() {
        assertEquals(SyncAge.JustNow, ageOf(Duration.ofSeconds(59)))
        assertEquals(SyncAge.Minutes(1), ageOf(Duration.ofSeconds(60)))
    }

    @Test
    fun `1시간이 되면 시간으로 넘어간다`() {
        assertEquals(SyncAge.Minutes(59), ageOf(Duration.ofMinutes(59).plusSeconds(59)))
        assertEquals(SyncAge.Hours(1), ageOf(Duration.ofHours(1)))
    }

    /** 상대 시각에서 날짜로 **형식이 바뀌는 유일한 지점**이다. */
    @Test
    fun `하루가 되면 날짜로 바뀐다`() {
        assertEquals(SyncAge.Hours(23), ageOf(Duration.ofHours(23).plusMinutes(59)))

        val older = ageOf(Duration.ofHours(24))
        assertEquals(SyncAge.Older(LocalDate.of(2026, 10, 1)), older)
    }

    /**
     * 사용자가 기기 시간을 되돌리면 저장된 시각이 미래가 된다.
     * `-1시간 전`을 적느니 '방금'이 낫다. 없애는 것이 아니라 **그렇게 되도록** 고정한다.
     */
    @Test
    fun `미래 시각은 방금으로 본다`() {
        assertEquals(SyncAge.JustNow, ageOf(Duration.ofHours(-1)))
        assertEquals(SyncAge.JustNow, ageOf(Duration.ofDays(-30)))
    }

    /** 1970년이 들어와도 "56년 전"이 아니라 날짜로 적는다. */
    @Test
    fun `0이 저장돼 있어도 날짜로 적는다`() {
        assertEquals(SyncAge.Older(LocalDate.of(1970, 1, 1)), syncAgeOf(0L, clock))
    }

    /**
     * 날짜는 **시계의 시간대로** 환산한다.
     * UTC로 굳히면 자정 직후에 확인한 기록이 하루 밀려 적힌다.
     */
    @Test
    fun `날짜는 시계의 시간대를 따른다`() {
        // 한국 기준 10월 1일 오전 8시. UTC로는 9월 30일 23시다.
        val instant = Instant.parse("2026-09-30T23:00:00Z")
        val old = Duration.ofDays(2)

        val seoul = syncAgeOf(instant.toEpochMilli(), Clock.fixed(instant.plus(old), zone))
        val utc = syncAgeOf(instant.toEpochMilli(), Clock.fixed(instant.plus(old), ZoneId.of("UTC")))

        assertEquals(SyncAge.Older(LocalDate.of(2026, 10, 1)), seoul)
        assertEquals(SyncAge.Older(LocalDate.of(2026, 9, 30)), utc)
    }

    /**
     * 같은 버킷 안에서는 값이 같아야 한다.
     *
     * `StateFlow`는 같은 값을 흘리지 않으므로, 이 성질이 **다시 그리기와 스크린 리더
     * 재낭독을 막는 장치**가 된다. `Duration`을 그대로 노출하면 깨진다.
     */
    @Test
    fun `같은 버킷 안에서는 다시 세어도 같은 값이다`() {
        assertEquals(ageOf(Duration.ofHours(3)), ageOf(Duration.ofHours(3).plusMinutes(20)))
    }

    @Test
    fun `버킷을 넘기면 값이 달라진다`() {
        assertNotEquals(ageOf(Duration.ofHours(3)), ageOf(Duration.ofHours(4)))
    }

    /**
     * 분류와 갱신 간격이 어긋나면 라벨이 늦게 바뀐다.
     * 버킷을 늘리면서 갱신 간격을 잊으면 그 버킷은 영영 갱신되지 않는다.
     */
    @Test
    fun `버킷이 굵을수록 드물게 깨어난다`() {
        assertEquals(Duration.ofSeconds(10), nextSyncAgeRefresh(SyncAge.JustNow))
        assertEquals(Duration.ofMinutes(1), nextSyncAgeRefresh(SyncAge.Minutes(30)))
        assertEquals(Duration.ofMinutes(10), nextSyncAgeRefresh(SyncAge.Hours(5)))
    }

    /** 날짜 표기는 하루가 지나도 그대로고, 확인이 일어나면 저장소 쪽에서 알려 온다. */
    @Test
    fun `깨어날 이유가 없는 상태에서는 기다리지 않는다`() {
        assertNull(nextSyncAgeRefresh(SyncAge.NeverChecked))
        assertNull(nextSyncAgeRefresh(SyncAge.Older(LocalDate.of(2026, 9, 30))))
    }
}
