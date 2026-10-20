package com.dksystems.tracephone.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dksystems.tracephone.domain.model.UsageAlertSettings
import com.dksystems.tracephone.domain.preferences.UsageAlertPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "usage_alert_preferences"

private val Context.usageAlertDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * 경고 설정과 '오늘 이미 알린 앱' 기록을 DataStore에 저장한다.
 *
 * 알림 기록을 Room이 아니라 여기에 두는 이유는, 테이블을 추가하면 스키마 버전이 올라가고
 * 마이그레이션을 써야 하기 때문이다. 누적 사용량 DB는 잃으면 복구할 수 없어
 * 파괴적 마이그레이션을 금지해 두었으므로 테이블 추가는 그만큼 무거운 결정이 된다.
 *
 * 날짜 하나와 UID 집합만 남기고, 저장된 날짜가 오늘과 다르면 없는 것으로 취급한다.
 * 그래서 기록이 날짜별로 쌓여 커지는 일이 없다.
 */
@Singleton
class UsageAlertPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageAlertPreferences {

    private val storedPreferences: Flow<Preferences> = context.usageAlertDataStore.data
        .recoveringFromReadFailure(PREFERENCES_NAME, "기본 경고 설정을 사용합니다.")

    override val settings: Flow<UsageAlertSettings> = storedPreferences.map { preferences ->
        UsageAlertSettings(
            isEnabled = preferences[ENABLED_KEY] ?: UsageAlertSettings.DEFAULT.isEnabled,
            presetLimitBytes = preferences[PRESET_LIMIT_BYTES_KEY]
                ?: UsageAlertSettings.DEFAULT.presetLimitBytes,
            customLimitMegabytes = preferences[CUSTOM_LIMIT_MEGABYTES_KEY]
                ?: UsageAlertSettings.DEFAULT.customLimitMegabytes
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.usageAlertDataStore.edit { preferences ->
            preferences[ENABLED_KEY] = enabled
        }
    }

    override suspend fun setPresetLimitBytes(limitBytes: Long) {
        context.usageAlertDataStore.edit { preferences ->
            preferences[PRESET_LIMIT_BYTES_KEY] = limitBytes
        }
    }

    override suspend fun setCustomLimitMegabytes(limitMegabytes: Long) {
        context.usageAlertDataStore.edit { preferences ->
            preferences[CUSTOM_LIMIT_MEGABYTES_KEY] = limitMegabytes
        }
    }

    override suspend fun notifiedMultiplesOn(date: LocalDate): Map<Int, Int> {
        val preferences = storedPreferences.first()
        if (preferences[NOTIFIED_DATE_KEY] != date.toString()) {
            return emptyMap()
        }

        return preferences[NOTIFIED_MULTIPLES_KEY]
            .orEmpty()
            .mapNotNull(::toUidAndMultipleOrNull)
            .toMap()
    }

    override suspend fun markNotified(date: LocalDate, notifiedMultiples: Map<Int, Int>) {
        context.usageAlertDataStore.edit { preferences ->
            val isSameDay = preferences[NOTIFIED_DATE_KEY] == date.toString()
            val previous = if (isSameDay) {
                preferences[NOTIFIED_MULTIPLES_KEY].orEmpty()
                    .mapNotNull(::toUidAndMultipleOrNull)
                    .toMap()
            } else {
                emptyMap()
            }

            preferences[NOTIFIED_DATE_KEY] = date.toString()
            preferences[NOTIFIED_MULTIPLES_KEY] = (previous + notifiedMultiples)
                .map { (uid, multiple) -> "$uid$ENTRY_SEPARATOR$multiple" }
                .toSet()
        }
    }

    /**
     * `uid:배수` 형태의 한 줄을 해석한다. 알아볼 수 없으면 버린다.
     *
     * DataStore가 다룰 수 있는 형태가 제한적이라 문자열 집합에 담는다.
     * 저장된 글자가 망가져 있어도 알림이 안 오고 마는 정도라 예외로 앱을 멈출 이유가 없다.
     */
    private fun toUidAndMultipleOrNull(entry: String): Pair<Int, Int>? {
        val parts = entry.split(ENTRY_SEPARATOR)
        if (parts.size != ENTRY_PART_COUNT) {
            return null
        }

        val uid = parts[UID_INDEX].toIntOrNull() ?: return null
        val multiple = parts[MULTIPLE_INDEX].toIntOrNull() ?: return null
        return uid to multiple
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("alert_enabled")
        val PRESET_LIMIT_BYTES_KEY = longPreferencesKey("preset_limit_bytes")
        val CUSTOM_LIMIT_MEGABYTES_KEY = longPreferencesKey("custom_limit_megabytes")
        val NOTIFIED_DATE_KEY = stringPreferencesKey("notified_date")
        val NOTIFIED_MULTIPLES_KEY = stringSetPreferencesKey("notified_multiples")

        const val ENTRY_SEPARATOR = ":"
        const val ENTRY_PART_COUNT = 2
        const val UID_INDEX = 0
        const val MULTIPLE_INDEX = 1
    }
}
