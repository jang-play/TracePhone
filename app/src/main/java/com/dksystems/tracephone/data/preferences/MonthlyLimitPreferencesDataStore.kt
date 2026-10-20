package com.dksystems.tracephone.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dksystems.tracephone.domain.model.BillingCycle
import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "monthly_limit_preferences"

private val Context.monthlyLimitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * 요금제 한도 설정을 기기에 저장한다.
 *
 * 이미 알린 지점은 **주기 시작일 + 지점 이름 집합**으로 남긴다.
 * 저장된 시작일이 지금 주기와 다르면 없는 것으로 보므로 지난 주기 기록이 쌓이지 않는다.
 */
@Singleton
class MonthlyLimitPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : MonthlyLimitPreferences {

    private val storedPreferences: Flow<Preferences> = context.monthlyLimitDataStore.data
        .recoveringFromReadFailure(PREFERENCES_NAME, "기본값을 사용합니다.")

    override val settings: Flow<MonthlyLimitSettings> = storedPreferences.map { preferences ->
        MonthlyLimitSettings(
            isEnabled = preferences[ENABLED_KEY] ?: MonthlyLimitSettings.DEFAULT.isEnabled,
            limitMegabytes = preferences[LIMIT_MEGABYTES_KEY]
                ?: MonthlyLimitSettings.DEFAULT.limitMegabytes,
            cycleStartDay = preferences[CYCLE_START_DAY_KEY]
                ?: MonthlyLimitSettings.DEFAULT.cycleStartDay
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.monthlyLimitDataStore.edit { it[ENABLED_KEY] = enabled }
    }

    override suspend fun setLimitMegabytes(limitMegabytes: Long) {
        val safeValue = limitMegabytes.coerceIn(
            MonthlyLimitSettings.NO_LIMIT,
            MonthlyLimitSettings.MAX_LIMIT_MEGABYTES
        )
        context.monthlyLimitDataStore.edit { it[LIMIT_MEGABYTES_KEY] = safeValue }
    }

    override suspend fun setCycleStartDay(cycleStartDay: Int) {
        val safeValue =
            cycleStartDay.coerceIn(BillingCycle.MIN_START_DAY, BillingCycle.MAX_START_DAY)
        context.monthlyLimitDataStore.edit { it[CYCLE_START_DAY_KEY] = safeValue }
    }

    override suspend fun notifiedThresholdsIn(cycleStart: LocalDate): Set<MonthlyLimitThreshold> {
        val preferences = storedPreferences.first()
        if (preferences[NOTIFIED_CYCLE_START_KEY] != cycleStart.toString()) {
            return emptySet()
        }

        return preferences[NOTIFIED_THRESHOLDS_KEY]
            .orEmpty()
            .mapNotNull(::toThresholdOrNull)
            .toSet()
    }

    override suspend fun markNotified(cycleStart: LocalDate, threshold: MonthlyLimitThreshold) {
        context.monthlyLimitDataStore.edit { preferences ->
            val isSameCycle = preferences[NOTIFIED_CYCLE_START_KEY] == cycleStart.toString()
            val previous =
                if (isSameCycle) preferences[NOTIFIED_THRESHOLDS_KEY].orEmpty() else emptySet()

            preferences[NOTIFIED_CYCLE_START_KEY] = cycleStart.toString()
            preferences[NOTIFIED_THRESHOLDS_KEY] = previous + threshold.name
        }
    }

    /** 이름으로 저장하므로 열거형 순서를 바꿔도 값이 어긋나지 않는다. 모르는 이름은 버린다. */
    private fun toThresholdOrNull(storedName: String): MonthlyLimitThreshold? =
        MonthlyLimitThreshold.entries.firstOrNull { it.name == storedName }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("monthly_limit_enabled")
        val LIMIT_MEGABYTES_KEY = longPreferencesKey("monthly_limit_megabytes")
        val CYCLE_START_DAY_KEY = intPreferencesKey("cycle_start_day")
        val NOTIFIED_CYCLE_START_KEY = stringPreferencesKey("notified_cycle_start")
        val NOTIFIED_THRESHOLDS_KEY = stringSetPreferencesKey("notified_thresholds")
    }
}
