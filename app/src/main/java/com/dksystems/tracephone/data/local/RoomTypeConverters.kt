package com.dksystems.tracephone.data.local

import androidx.room.TypeConverter
import com.dksystems.tracephone.domain.model.NetworkKind
import java.time.LocalDate

/**
 * Room이 도메인 타입을 SQLite 컬럼으로 바꿀 때 쓰는 변환기.
 *
 * 날짜는 ISO 문자열("2026-09-03")로 저장한다.
 * 사전순 정렬이 곧 날짜순 정렬이라 `BETWEEN` 범위 조회가 그대로 동작하고,
 * DB를 직접 열어봤을 때 읽히는 값이라 디버깅이 쉽다.
 */
class RoomTypeConverters {

    @TypeConverter
    fun localDateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun textToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    @TypeConverter
    fun networkKindToText(kind: NetworkKind?): String? = kind?.name

    @TypeConverter
    fun textToNetworkKind(text: String?): NetworkKind? = text?.let(NetworkKind::valueOf)
}
