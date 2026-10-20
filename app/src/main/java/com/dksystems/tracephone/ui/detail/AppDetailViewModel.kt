package com.dksystems.tracephone.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.domain.model.DailyUsagePoint
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.usecase.GetAppDailyTrendUseCase
import com.dksystems.tracephone.domain.usecase.GetAppIdentityUseCase
import com.dksystems.tracephone.domain.usecase.GetUsageStateSplitUseCase
import com.dksystems.tracephone.ui.SUBSCRIPTION_TIMEOUT_MILLIS
import com.dksystems.tracephone.ui.navigation.TracePhoneDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * 앱 상세 화면의 상태.
 *
 * @property period 요약 화면에서 보고 있던 기간을 그대로 이어받은 값
 * @property dailyPoints 기간 내 날짜들. 기록이 없는 날도 0으로 채워져 들어온다.
 * @property stateSplit 같은 기간을 회선별로, 다시 직접 사용 / 백그라운드 / 구분 없음으로 나눈 값
 */
data class AppDetailUiState(
    val uid: Int,
    val period: UsagePeriod,
    val appLabel: String = "",
    val packageName: String? = null,
    /** 설치 목록에 없는 앱인지. 기록이 앱보다 오래 남았다는 뜻이다. */
    val isAppInstalled: Boolean = true,
    val dailyPoints: List<DailyUsagePoint> = emptyList(),
    val stateSplit: NetworkUsageSplit = NetworkUsageSplit.ZERO,
    val isLoading: Boolean = true
) {

    val mobileTotalBytes: Long
        get() = dailyPoints.sumOf { it.mobile.totalBytes }

    val wifiTotalBytes: Long
        get() = dailyPoints.sumOf { it.wifi.totalBytes }

    val totalBytes: Long
        get() = mobileTotalBytes + wifiTotalBytes

    /** 기록이 하나도 없으면 그래프 대신 안내 문구를 보여준다. */
    val hasUsage: Boolean
        get() = totalBytes > 0L
}

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getAppDailyTrend: GetAppDailyTrendUseCase,
    getAppIdentity: GetAppIdentityUseCase,
    getUsageStateSplit: GetUsageStateSplitUseCase
) : ViewModel() {

    private val uid: Int = requireNotNull(savedStateHandle[TracePhoneDestinations.ARG_UID]) {
        "앱 상세 화면에 uid 인자가 전달되지 않았습니다."
    }

    /**
     * 요약 화면에서 보고 있던 기간을 그대로 이어받는다.
     *
     * 여기서 오늘 날짜로 기간을 다시 계산하면, 사용자가 지난주를 보다가 앱을 눌렀을 때
     * 상세 화면만 이번 주를 보여주게 되어 두 화면의 수치가 어긋난다.
     */
    private val period: UsagePeriod = UsagePeriod(
        startDate = requiredDateArgument(savedStateHandle, TracePhoneDestinations.ARG_START_DATE),
        endDateInclusive = requiredDateArgument(
            savedStateHandle,
            TracePhoneDestinations.ARG_END_DATE
        )
    )

    val uiState: StateFlow<AppDetailUiState> = combine(
        // 앱 정보는 한 번만 구하면 되므로 단발 Flow로 감싼다.
        flow { emit(getAppIdentity(uid)) },
        getAppDailyTrend(uid, period),
        getUsageStateSplit(uid, period)
    ) { identity, dailyPoints, stateSplit ->
        AppDetailUiState(
            uid = uid,
            period = period,
            appLabel = identity.label,
            packageName = identity.packageName,
            isAppInstalled = identity.isInstalled,
            dailyPoints = dailyPoints,
            stateSplit = stateSplit,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AppDetailUiState(uid = uid, period = period)
    )

    private fun requiredDateArgument(
        savedStateHandle: SavedStateHandle,
        argumentName: String
    ): LocalDate {
        val rawValue: String = requireNotNull(savedStateHandle[argumentName]) {
            "앱 상세 화면에 $argumentName 인자가 전달되지 않았습니다."
        }
        return LocalDate.parse(rawValue)
    }
}
