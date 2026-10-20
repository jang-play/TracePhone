package com.dksystems.tracephone.di

import android.content.Context
import androidx.room.Room
import com.dksystems.tracephone.core.DatabaseConstants
import com.dksystems.tracephone.data.local.DailyAppUsageDao
import com.dksystems.tracephone.data.local.TracePhoneDatabase
import com.dksystems.tracephone.data.local.TracePhoneMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TracePhoneDatabase =
        Room.databaseBuilder(
            context,
            TracePhoneDatabase::class.java,
            DatabaseConstants.DATABASE_NAME
        )
            .addMigrations(*TracePhoneMigrations.ALL)
            .build()
        // 파괴적 마이그레이션을 의도적으로 설정하지 않는다.
        // 이 DB의 누적 기록은 시스템이 지운 뒤에도 남는 유일한 자산이라 잃으면 복구할 수 없다.
        // 마이그레이션이 빠지면 앱이 켜질 때 예외로 즉시 드러나며, 조용히 지워지는 일은 없다.

    @Provides
    fun provideDailyAppUsageDao(database: TracePhoneDatabase): DailyAppUsageDao =
        database.dailyAppUsageDao()

    /**
     * '오늘'을 주입으로 받기 위한 시계.
     *
     * `LocalDate.now()`를 직접 부르면 날짜 계산 로직을 테스트할 수 없다.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
