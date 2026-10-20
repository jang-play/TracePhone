package com.dksystems.tracephone.ui.hourly

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.domain.model.HourlyUsagePoint
import com.dksystems.tracephone.domain.usecase.GetAppIdentityUseCase
import com.dksystems.tracephone.domain.usecase.GetHourlyUsageUseCase
import com.dksystems.tracephone.ui.navigation.TracePhoneDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 시간대별 사용량 화면의 상태.
 *
 * @property hourlyPoints 0시부터 23시까지 24개. 기록이 없는 시간대도 0으로 들어 있다.
 */
data class HourlyUsageUiState(
    val date: LocalDate,
    val appLabel: String = "",
    val hourlyPoints: List<HourlyUsagePoint> = emptyList(),
    val isLoading: Boolean = true
) {

    val mobileTotalBytes: Long
        get() = hourlyPoints.sumOf { it.mobile.totalBytes }

    val wifiTotalBytes: Long
        get() = hourlyPoints.sumOf { it.wifi.totalBytes }

    val totalBytes: Long
        get() = mobileTotalBytes + wifiTotalBytes

    /**
     * 시스템의 상세 기록 보관 기간을 벗어난 날짜는 전부 0으로 돌아온다.
     * 그때는 그래프 대신 안내 문구를 보여준다.
     */
    val hasUsage: Boolean
        get() = totalBytes > 0L
}

@HiltViewModel
class HourlyUsageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getHourlyUsage: GetHourlyUsageUseCase,
    private val getAppIdentity: GetAppIdentityUseCase
) : ViewModel() {

    private val uid: Int = requireNotNull(savedStateHandle[TracePhoneDestinations.ARG_UID]) {
        "시간대별 화면에 uid 인자가 전달되지 않았습니다."
    }

    private val date: LocalDate = LocalDate.parse(
        requireNotNull(savedStateHandle[TracePhoneDestinations.ARG_DATE]) {
            "시간대별 화면에 날짜 인자가 전달되지 않았습니다."
        }
    )

    private val _uiState = MutableStateFlow(HourlyUsageUiState(date = date))
    val uiState: StateFlow<HourlyUsageUiState> = _uiState.asStateFlow()

    init {
        // 시스템을 직접 조회하므로 Flow가 아니라 한 번 읽어 담는다.
        // 지난 날짜의 값은 더 이상 변하지 않아 계속 관찰할 이유가 없다.
        viewModelScope.launch {
            val identity = getAppIdentity(uid)
            val points = getHourlyUsage(uid, date)

            _uiState.value = HourlyUsageUiState(
                date = date,
                appLabel = identity.label,
                hourlyPoints = points,
                isLoading = false
            )
        }
    }
}
