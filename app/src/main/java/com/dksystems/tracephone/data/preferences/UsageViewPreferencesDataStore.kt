package com.dksystems.tracephone.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dksystems.tracephone.domain.model.AppListLayout
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.domain.preferences.UsageViewPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "usage_view_preferences"

private val Context.usageViewDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * 조회 조건을 DataStore에 저장한다.
 *
 * 저장 형식과 알 수 없는 값의 처리는 [toEnumOrDefault]에 적어 두었다.
 */
@Singleton
class UsageViewPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageViewPreferences {

    private val storedPreferences: Flow<Preferences> = context.usageViewDataStore.data
        .recoveringFromReadFailure(PREFERENCES_NAME, "기본 조회 조건으로 시작합니다.")

    override val periodUnit: Flow<PeriodUnit> = storedPreferences.map { preferences ->
        preferences[PERIOD_UNIT_KEY].toEnumOrDefault(DEFAULT_PERIOD_UNIT)
    }

    override val sortOrder: Flow<UsageSortOrder> = storedPreferences.map { preferences ->
        preferences[SORT_ORDER_KEY].toEnumOrDefault(DEFAULT_SORT_ORDER)
    }

    override val appListLayout: Flow<AppListLayout> = storedPreferences.map { preferences ->
        preferences[APP_LIST_LAYOUT_KEY].toEnumOrDefault(DEFAULT_APP_LIST_LAYOUT)
    }

    override suspend fun setPeriodUnit(unit: PeriodUnit) {
        context.usageViewDataStore.edit { preferences ->
            preferences[PERIOD_UNIT_KEY] = unit.name
        }
    }

    override suspend fun setSortOrder(order: UsageSortOrder) {
        context.usageViewDataStore.edit { preferences ->
            preferences[SORT_ORDER_KEY] = order.name
        }
    }

    override suspend fun setAppListLayout(layout: AppListLayout) {
        context.usageViewDataStore.edit { preferences ->
            preferences[APP_LIST_LAYOUT_KEY] = layout.name
        }
    }

    private companion object {
        val PERIOD_UNIT_KEY = stringPreferencesKey("period_unit")
        val SORT_ORDER_KEY = stringPreferencesKey("sort_order")
        val APP_LIST_LAYOUT_KEY = stringPreferencesKey("app_list_layout")

        val DEFAULT_PERIOD_UNIT = PeriodUnit.MONTH

        /**
         * 요금이 붙는 쪽이 모바일이고 목록의 큰 숫자도 모바일이라, 순서도 거기 맞춘다.
         *
         * **이미 쓰던 기기는 바뀌지 않는다.** 저장된 값이 있으면 그것을 읽으므로
         * 합계순을 골라 둔 사용자의 선택을 덮어쓰지 않는다.
         */
        val DEFAULT_SORT_ORDER = UsageSortOrder.MOBILE

        /** 지금까지의 모습에 가까운 쪽을 기본으로 둔다. */
        val DEFAULT_APP_LIST_LAYOUT = AppListLayout.DETAILED
    }
}
