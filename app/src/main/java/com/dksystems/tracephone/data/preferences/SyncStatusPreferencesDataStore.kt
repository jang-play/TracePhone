package com.dksystems.tracephone.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dksystems.tracephone.domain.preferences.SyncStatusPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "sync_status_preferences"

private val Context.syncStatusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * 마지막으로 확인에 성공한 시각을 DataStore에 남긴다.
 *
 * 읽기에 실패하면 "확인한 적 없음"으로 돌아간다. 화면은 그때 '아직 확인하지 않았습니다'를
 * 적는데, 멀쩡한 앱을 한 번 그렇게 보이는 쪽이 틀린 시각을 적는 것보다 낫다.
 */
@Singleton
class SyncStatusPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncStatusPreferences {

    override val lastSuccessfulSyncAtMillis: Flow<Long?> = context.syncStatusDataStore.data
        .recoveringFromReadFailure(PREFERENCES_NAME, "확인 시각을 비워 둡니다.")
        .map { preferences -> preferences[LAST_SUCCESSFUL_SYNC_KEY] }

    override suspend fun recordSuccessfulSync(atEpochMillis: Long) {
        context.syncStatusDataStore.edit { preferences ->
            preferences[LAST_SUCCESSFUL_SYNC_KEY] = atEpochMillis
        }
    }

    private companion object {
        val LAST_SUCCESSFUL_SYNC_KEY = longPreferencesKey("last_successful_sync_at_millis")
    }
}
