package com.dksystems.tracephone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 누적 사용량 데이터베이스.
 *
 * 이 DB의 내용은 시스템이 통계를 지운 뒤에도 남는, 앱의 유일한 영구 자산이다.
 * 따라서 스키마를 바꿀 때는 반드시 마이그레이션을 작성한다. 파괴적 마이그레이션을 쓰지 않는다.
 */
@Database(
    entities = [DailyAppUsageEntity::class],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(RoomTypeConverters::class)
abstract class TracePhoneDatabase : RoomDatabase() {

    abstract fun dailyAppUsageDao(): DailyAppUsageDao
}

/** 스키마 버전. 2에서 포그라운드 몫을 담는 열이 추가되었다. */
const val DATABASE_VERSION = 2
