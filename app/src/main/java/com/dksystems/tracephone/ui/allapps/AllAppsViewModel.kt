package com.dksystems.tracephone.ui.allapps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.data.preferences.toEnumOrDefault
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.domain.usecase.GetAppUsageUseCase
import com.dksystems.tracephone.ui.SUBSCRIPTION_TIMEOUT_MILLIS
import com.dksystems.tracephone.ui.navigation.TracePhoneDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * 이 화면이 재는 것. **모바일 하나뿐이다.**
 *
 * 요약 화면의 정렬 기준을 이어받지 않는다. Wi-Fi순으로 세워 놓고 막대는 모바일을 그리면
 * 막대 길이가 내림차순이 아니게 되어 **고장난 화면처럼 보인다.** 한 화면에 30줄이 깔리는
 * 곳에서 그 어긋남은 숨을 데가 없다.
 *
 * 모바일로 고정하는 것은 이 앱의 결론과도 맞는다 - 요금이 붙는 쪽이고, 요약 화면의
 * 큰 숫자도 목록의 큰 숫자도 모바일이다.
 */
private val FIXED_SORT_ORDER = UsageSortOrder.MOBILE

/**
 * 기간 단위를 알아볼 수 없을 때 제목에 쓸 값.
 *
 * `WEEK`가 뜻밖으로 보이지만 의도한 것이다 - 이 단위의 제목 형식은 `10월 1일 ~ 10월 31일`처럼
 * **받은 날짜 범위를 그대로 적는다.** 어떤 기간에도 거짓이 되지 않는 유일한 형식이다.
 * `MONTH`로 돌리면 하루짜리 기간에 `2026년 10월`이라고 적게 된다.
 */
private val FALLBACK_PERIOD_UNIT = PeriodUnit.WEEK

/**
 * '모든 앱' 화면의 상태.
 *
 * @property period 요약 화면에서 보고 있던 기간을 그대로 이어받은 값
 * @property periodUnit 제목에 기간을 적는 데 쓴다. 날짜 범위만으로는 '일'인지 '주'인지 알 수 없다.
 */
data class AllAppsUiState(
    val period: UsagePeriod,
    val periodUnit: PeriodUnit,
    val appUsages: List<AppUsage> = emptyList(),
    val isLoading: Boolean = true
) {

    /**
     * 막대의 자이면서 몫의 분모. **거르기 전 목록에서 더한다.**
     *
     * 걸러낸 뒤에 더해도 같은 값이 나오지만(거르는 조건이 '모바일이 0'이므로),
     * 요약 화면이 정렬을 바꿀 때 Wi-Fi 총량이 흔들린 전례가 있어 같은 규칙을 지킨다.
     */
    val mobileTotalBytes: Long by lazy { appUsages.sumOf { it.mobile.totalBytes } }

    /**
     * 실제로 그릴 줄들.
     *
     * 모바일을 쓰지 않은 앱은 뺀다. 두면 빈 막대와 `0 B`가 수십 줄 깔려
     * **한 화면에 많이 보여주려고 만든 화면의 자리를 그것들이 차지한다.**
     */
    val visibleAppUsages: List<AppUsage> by lazy {
        appUsages.filter { it.mobile.totalBytes > 0L }
    }
}

/**
 * 한 기간의 모든 앱을 한 화면에 늘어놓는 화면의 상태 공급자.
 *
 * 기간을 **새로 고르게 하지 않는다.** 요약 화면이 이미 네 단위와 앞뒤 이동을 다 갖고 있어,
 * 여기에 같은 기계를 두면 두 벌이 된다. 넘겨받은 날짜 범위만 읽는다.
 */
@HiltViewModel
class AllAppsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getAppUsage: GetAppUsageUseCase
) : ViewModel() {

    /**
     * 요약 화면에서 보고 있던 기간을 그대로 이어받는다.
     *
     * 여기서 오늘 날짜로 다시 계산하면, 지난주를 보다가 들어온 사용자에게 이번 주를
     * 보여주게 되어 두 화면의 합계가 어긋난다. 앱 상세 화면이 같은 이유로 같은 방식을 쓴다.
     */
    private val period: UsagePeriod = UsagePeriod(
        startDate = requiredDateArgument(savedStateHandle, TracePhoneDestinations.ARG_START_DATE),
        endDateInclusive = requiredDateArgument(
            savedStateHandle,
            TracePhoneDestinations.ARG_END_DATE
        )
    )

    /**
     * 제목에 기간을 어떻게 적을지 정하는 값.
     *
     * **알아볼 수 없으면 던지지 않고 [FALLBACK_PERIOD_UNIT]으로 돌린다.** 날짜와 달리
     * 열거형 이름은 **버전이 올라가며 사라질 수 있다.** 프로세스가 죽은 뒤 저장된 경로에서
     * 복원되는 자리라, 그 사이에 앱이 갱신돼 항목이 없어졌다면 `valueOf`가 화면이 만들어지는
     * 지점에서 던진다. 작업 목록을 지울 때까지 반복해서 죽는다 - 제목 하나에 치를 대가가 아니다.
     *
     * 저장된 이름을 되읽는 규칙은 설정 쪽과 같은 것을 쓴다([toEnumOrDefault]).
     */
    private val periodUnit: PeriodUnit =
        savedStateHandle.get<String>(TracePhoneDestinations.ARG_PERIOD_UNIT)
            .toEnumOrDefault(FALLBACK_PERIOD_UNIT)

    val uiState: StateFlow<AllAppsUiState> = getAppUsage(period, FIXED_SORT_ORDER)
        .map { appUsages ->
            AllAppsUiState(
                period = period,
                periodUnit = periodUnit,
                appUsages = appUsages,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = AllAppsUiState(period = period, periodUnit = periodUnit)
        )

    private fun requiredDateArgument(
        savedStateHandle: SavedStateHandle,
        argumentName: String
    ): LocalDate {
        val rawValue: String = requireNotNull(savedStateHandle[argumentName]) {
            "모든 앱 화면에 $argumentName 인자가 전달되지 않았습니다."
        }
        return LocalDate.parse(rawValue)
    }
}
