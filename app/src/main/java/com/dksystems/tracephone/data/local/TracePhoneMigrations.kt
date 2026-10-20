package com.dksystems.tracephone.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 이 DB의 누적 기록은 시스템이 통계를 지운 뒤에도 남는, 다시 만들 수 없는 자산이다.
 * 그래서 모든 스키마 변경은 여기에 마이그레이션으로 남기고 파괴적 마이그레이션은 쓰지 않는다.
 */
object TracePhoneMigrations {

    /**
     * 포그라운드 몫을 담을 열 두 개를 더한다.
     *
     * 기본값을 주지 않는 것이 중요하다. 기존 행은 null로 남아야 한다.
     * 0을 채우면 지난 기록이 전부 백그라운드 사용이었던 것처럼 보이는데,
     * 그것은 사실이 아니라 모르는 것이다.
     *
     * 기본키를 건드리지 않으므로 테이블을 다시 만들 필요가 없다. 기존 행은 그대로 남는다.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE daily_app_usage ADD COLUMN foregroundReceivedBytes INTEGER"
            )
            connection.execSQL(
                "ALTER TABLE daily_app_usage ADD COLUMN foregroundTransmittedBytes INTEGER"
            )
        }
    }

    /** 등록 순서대로 적용된다. 새 마이그레이션은 뒤에 덧붙인다. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
