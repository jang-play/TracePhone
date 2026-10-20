package com.dksystems.tracephone.di

import com.dksystems.tracephone.data.notification.SystemMonthlyLimitNotifier
import com.dksystems.tracephone.data.notification.SystemUsageAlertNotifier
import com.dksystems.tracephone.data.preferences.MonthlyLimitPreferencesDataStore
import com.dksystems.tracephone.data.preferences.SyncStatusPreferencesDataStore
import com.dksystems.tracephone.data.preferences.ThemePreferencesDataStore
import com.dksystems.tracephone.data.preferences.UsageAlertPreferencesDataStore
import com.dksystems.tracephone.data.preferences.UsageViewPreferencesDataStore
import com.dksystems.tracephone.data.preferences.WidgetAppearancePreferencesDataStore
import com.dksystems.tracephone.data.repository.SystemUsageReaderImpl
import com.dksystems.tracephone.data.repository.UsageRepositoryImpl
import com.dksystems.tracephone.data.repository.UsageSynchronizerImpl
import com.dksystems.tracephone.data.system.SystemThemeModeApplier
import com.dksystems.tracephone.data.update.BuildConfigAppVersion
import com.dksystems.tracephone.data.update.HttpLatestReleaseSource
import com.dksystems.tracephone.data.system.UsagePermissionChecker
import com.dksystems.tracephone.domain.alert.MonthlyLimitNotifier
import com.dksystems.tracephone.domain.alert.UsageAlertNotifier
import com.dksystems.tracephone.domain.appearance.ThemeModeApplier
import com.dksystems.tracephone.domain.permission.UsageAccessPermission
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import com.dksystems.tracephone.domain.preferences.SyncStatusPreferences
import com.dksystems.tracephone.domain.preferences.ThemePreferences
import com.dksystems.tracephone.domain.preferences.UsageAlertPreferences
import com.dksystems.tracephone.domain.preferences.UsageViewPreferences
import com.dksystems.tracephone.domain.preferences.WidgetAppearancePreferences
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import com.dksystems.tracephone.domain.repository.UsageRepository
import com.dksystems.tracephone.domain.repository.UsageSynchronizer
import com.dksystems.tracephone.domain.update.InstalledAppVersion
import com.dksystems.tracephone.domain.update.LatestReleaseSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 도메인 인터페이스를 데이터 레이어 구현체에 연결한다.
 *
 * 화면과 유스케이스는 인터페이스만 알기 때문에 테스트에서 가짜 구현으로 바꿔 끼울 수 있다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUsageRepository(implementation: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindSystemUsageReader(
        implementation: SystemUsageReaderImpl
    ): SystemUsageReader

    @Binds
    @Singleton
    abstract fun bindUsageSynchronizer(
        implementation: UsageSynchronizerImpl
    ): UsageSynchronizer

    @Binds
    @Singleton
    abstract fun bindUsageAccessPermission(
        implementation: UsagePermissionChecker
    ): UsageAccessPermission

    @Binds
    @Singleton
    abstract fun bindUsageViewPreferences(
        implementation: UsageViewPreferencesDataStore
    ): UsageViewPreferences

    @Binds
    @Singleton
    abstract fun bindUsageAlertPreferences(
        implementation: UsageAlertPreferencesDataStore
    ): UsageAlertPreferences

    @Binds
    @Singleton
    abstract fun bindUsageAlertNotifier(
        implementation: SystemUsageAlertNotifier
    ): UsageAlertNotifier

    @Binds
    @Singleton
    abstract fun bindMonthlyLimitPreferences(
        implementation: MonthlyLimitPreferencesDataStore
    ): MonthlyLimitPreferences

    @Binds
    @Singleton
    abstract fun bindThemeModeApplier(
        implementation: SystemThemeModeApplier
    ): ThemeModeApplier

    @Binds
    @Singleton
    abstract fun bindThemePreferences(
        implementation: ThemePreferencesDataStore
    ): ThemePreferences

    @Binds
    @Singleton
    abstract fun bindSyncStatusPreferences(
        implementation: SyncStatusPreferencesDataStore
    ): SyncStatusPreferences

    @Binds
    @Singleton
    abstract fun bindWidgetAppearancePreferences(
        implementation: WidgetAppearancePreferencesDataStore
    ): WidgetAppearancePreferences

    @Binds
    @Singleton
    abstract fun bindMonthlyLimitNotifier(
        implementation: SystemMonthlyLimitNotifier
    ): MonthlyLimitNotifier

    @Binds
    @Singleton
    abstract fun bindLatestReleaseSource(
        implementation: HttpLatestReleaseSource
    ): LatestReleaseSource

    @Binds
    @Singleton
    abstract fun bindInstalledAppVersion(
        implementation: BuildConfigAppVersion
    ): InstalledAppVersion
}
