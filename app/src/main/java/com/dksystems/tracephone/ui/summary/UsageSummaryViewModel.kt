package com.dksystems.tracephone.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.PeriodSelection
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.domain.model.AppListLayout
import com.dksystems.tracephone.core.SyncAge
import com.dksystems.tracephone.core.coroutineFailureLogger
import com.dksystems.tracephone.core.nextSyncAgeRefresh
import com.dksystems.tracephone.core.syncAgeOf
import com.dksystems.tracephone.domain.preferences.SyncStatusPreferences
import com.dksystems.tracephone.domain.preferences.UsageViewPreferences
import com.dksystems.tracephone.domain.repository.UsageRepository
import com.dksystems.tracephone.domain.usecase.EvaluateUsageAlertsUseCase
import com.dksystems.tracephone.domain.usecase.GetAppUsageUseCase
import com.dksystems.tracephone.domain.usecase.ObserveMonthlyLimitStatusUseCase
import com.dksystems.tracephone.ui.SUBSCRIPTION_TIMEOUT_MILLIS
import com.dksystems.tracephone.work.UsageRefresher
import com.dksystems.tracephone.work.UsageSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 사용량 요약 화면의 상태.
 *
 * 합계는 [appUsages]에서 파생되는 값이라 별도 필드로 두지 않는다.
 * 따로 두면 목록과 합계가 어긋난 상태가 존재할 수 있게 된다.
 *
 * **합계는 [appUsages]에서, 목록은 [visibleAppUsages]에서 나온다.** 둘을 같은 값에서 뽑으면
 * 정렬 칩을 눌렀을 뿐인데 그 기간의 Wi-Fi 총량이 달라진다. 실기기에서 겪었다.
 *
 * @property period [selection]을 오늘 기준으로 풀어낸 실제 날짜 범위. 화면 표시와 상세 이동에 함께 쓴다.
 * @property canGoPrevious 더 과거에 기록이 남아 있는지. 없으면 화살표를 비활성화한다.
 * @property canGoNext 현재 칸보다 뒤로는 갈 수 없다. 미래에는 기록이 없기 때문이다.
 */
data class UsageSummaryUiState(
    val selection: PeriodSelection = PeriodSelection.DEFAULT,
    val period: UsagePeriod,
    val sortOrder: UsageSortOrder = UsageSortOrder.MOBILE,
    val appUsages: List<AppUsage> = emptyList(),
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true
) {

    /**
     * 목록에서 뽑아내는 값들. **한 번만 세고 붙들어 둔다.**
     *
     * `get()`으로 두면 읽을 때마다 목록 전체를 훑는다. 합계 카드 한 곳에서만 세 번,
     * 목록을 그릴 때 두 번 더 읽히므로 같은 덧셈을 예닐곱 번 되풀이하게 된다.
     *
     * 이 클래스는 값이 바뀔 때마다 새로 만들어지므로 붙들어 둔 값이 낡을 일이 없다.
     * 생성자 밖의 프로퍼티라 `equals`/`copy`에도 영향을 주지 않는다.
     */
    val mobileTotalBytes: Long by lazy { appUsages.sumOf { it.mobile.totalBytes } }

    val wifiTotalBytes: Long by lazy { appUsages.sumOf { it.wifi.totalBytes } }

    val totalBytes: Long by lazy { mobileTotalBytes + wifiTotalBytes }

    /**
     * 목록에 그릴 앱들. **지금 세우는 기준으로 쓴 적이 없는 앱은 뺀다.**
     *
     * 실기기에서 최근 30일 192개 앱 중 **61개(32%)가 모바일 0**이었다 —
     * 모바일순 목록의 3분의 1이 큰 숫자 자리에 `0 B`를 달고 있었다.
     *
     * ### 합계보다 **뒤**에서 거르는 이유
     * 위의 합계들은 [appUsages] 전체에서 나온다. 거르고 난 목록을 더하면
     * **정렬을 바꿀 때마다 그 기간의 Wi-Fi 총량이 달라진다.**
     * 실기기에서 같은 10월이 모바일순 5.49 GB, Wi-Fi순 6.38 GB로 보였다.
     * 합계는 기간의 사실이지 목록의 사실이 아니다.
     *
     * 무엇을 '썼다'로 볼지는 [UsageSortOrder.visibleBytesOf]가 정한다.
     * 그 함수를 [UsageSortOrder.sortKeyOf]와 합치면 백그라운드순에서 앱이 사라진다.
     */
    val visibleAppUsages: List<AppUsage> by lazy {
        appUsages.filter { sortOrder.visibleBytesOf(it) > 0L }
    }
}

@HiltViewModel
class UsageSummaryViewModel @Inject constructor(
    private val getAppUsage: GetAppUsageUseCase,
    private val usageRepository: UsageRepository,
    private val usageViewPreferences: UsageViewPreferences,
    private val usageSyncScheduler: UsageSyncScheduler,
    private val usageRefresher: UsageRefresher,
    private val evaluateUsageAlerts: EvaluateUsageAlertsUseCase,
    observeMonthlyLimitStatus: ObserveMonthlyLimitStatusUseCase,
    syncStatusPreferences: SyncStatusPreferences,
    private val clock: Clock
) : ViewModel() {

    private val selection = MutableStateFlow(PeriodSelection.DEFAULT)
    private val sortOrder = MutableStateFlow(UsageSortOrder.MOBILE)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UsageSummaryUiState> = combine(
        selection,
        sortOrder,
        usageRepository.observeEarliestRecordedDate()
    ) { currentSelection, currentSortOrder, earliestRecordedDate ->
        buildQuery(currentSelection, currentSortOrder, earliestRecordedDate)
    }.flatMapLatest { query ->
        getAppUsage(query.period, query.sortOrder)
            .map { usages -> query.toUiState(usages, isLoading = false) }
            // 기간을 바꾼 직후 이전 기간의 목록이 남아 있으면 잘못된 수치로 읽힌다.
            // 새 결과가 오기 전까지 로딩 상태를 먼저 내보낸다.
            .onStart { emit(query.toUiState(emptyList(), isLoading = true)) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = initialUiState()
    )

    /**
     * 요금제 한도 현황. 한도를 쓰지 않으면 null이라 카드를 그리지 않는다.
     *
     * 목록 상태와 따로 두는 이유는 두 값의 기간이 다르기 때문이다.
     * 목록은 사용자가 고른 기간을, 한도는 청구 주기를 본다. 한 상태로 묶으면
     * 기간을 옮길 때마다 한도까지 다시 계산하게 된다.
     */
    val monthlyLimitStatus: StateFlow<MonthlyLimitStatus?> = observeMonthlyLimitStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null
        )

    /**
     * 목록을 그리는 모양. 설정 화면에서 바꾼 값이 곧바로 반영되도록 계속 관찰한다.
     *
     * 조회 조건([restoreLastViewedConditions])과 달리 한 번만 읽지 않는다.
     * 설정을 바꾸고 돌아왔을 때 목록이 그대로면 바뀐 줄 모른다.
     */
    val appListLayout: StateFlow<AppListLayout> = usageViewPreferences.appListLayout
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = AppListLayout.DETAILED
        )

    /**
     * 앱이 마지막으로 사용량을 확인한 지 얼마나 됐는가.
     *
     * 저장된 시각이 바뀌면 다시 세고, 바뀌지 않아도 **스스로 깨어나** 다시 센다.
     * 화면을 켜 둔 채로 두면 '방금'이 '5분 전'이 되어야 하기 때문이다.
     *
     * 깨어나는 간격은 [nextSyncAgeRefresh]가 현재 단계를 보고 정한다. 고정 간격이 아니라
     * '시간' 단위에서는 드물게, 날짜로 적는 단계에서는 **아예 깨어나지 않는다.**
     *
     * [SyncAge]가 단계로 나뉘어 있어 같은 단계 안에서 다시 세면 값이 같고,
     * `StateFlow`는 같은 값을 흘리지 않는다. 덕분에 깨어나도 화면이 다시 그려지지 않고
     * 스크린 리더가 같은 말을 되풀이하지도 않는다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val syncAge: StateFlow<SyncAge> = syncStatusPreferences.lastSuccessfulSyncAtMillis
        .flatMapLatest { lastSuccessAtMillis -> syncAgeUpdates(lastSuccessAtMillis) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = SyncAge.NeverChecked
        )

    /** 더 깨어날 이유가 없어질 때까지 다시 세어 내보낸다. */
    private fun syncAgeUpdates(lastSuccessAtMillis: Long?): Flow<SyncAge> = flow {
        while (true) {
            val age = syncAgeOf(lastSuccessAtMillis, clock)
            emit(age)

            val waitFor = nextSyncAgeRefresh(age) ?: break
            delay(waitFor.toMillis())
        }
    }

    init {
        restoreLastViewedConditions()

        // 화면에 들어온 김에 당일 최신값을 반영한다.
        // 최초 실행이면 유스케이스가 알아서 긴 백필로 전환한다.
        usageSyncScheduler.requestImmediateSync()

        // 15분 주기 워커를 기다리지 않고 지금 바로 판정한다.
        // 설정을 막 바꾼 직후에도 결과가 즉시 보이게 하기 위해서다.
        //
        // 판정은 '이미 알린 앱'을 설정에 쓴다. 저장이 실패하면 예외가 올라오고, 받는 곳이
        // 없으면 **앱을 켜는 것만으로** 프로세스가 죽는다. 그래서 처리기를 함께 건다.
        viewModelScope.launch(coroutineFailureLogger) { evaluateUsageAlerts() }
    }

    /** 당김 새로고침이 돌고 있는가. 표시를 켜고 끄는 데 쓴다. */
    val isRefreshing: StateFlow<Boolean> = usageRefresher.isRefreshing

    /**
     * 사용자가 목록을 당겨 새로고침했다.
     *
     * **현재 기간을 보고 있을 때만** 화면이 이 동작을 허용한다. 지난 기간의 합계는 이미
     * 저장된 기록이라 다시 물어도 같은 값이 나오고, 돌려봐야 사용자의 시간만 쓴다.
     */
    fun refresh() = usageRefresher.refresh()

    fun selectUnit(unit: PeriodUnit) {
        selection.update { it.withUnit(unit) }
        viewModelScope.launch(coroutineFailureLogger) { usageViewPreferences.setPeriodUnit(unit) }
    }

    /** 한 칸 과거로 이동한다. 더 갈 곳이 없으면 화면이 화살표를 막으므로 여기서는 확인하지 않는다. */
    fun showPreviousPeriod() {
        selection.update { it.previous() }
    }

    fun showNextPeriod() {
        selection.update { it.next() }
    }

    /** 몇 칸을 들어갔든 한 번에 현재로 돌아온다. */
    fun showCurrentPeriod() {
        selection.update { it.current() }
    }

    fun selectSortOrder(order: UsageSortOrder) {
        sortOrder.update { order }
        viewModelScope.launch(coroutineFailureLogger) { usageViewPreferences.setSortOrder(order) }
    }

    /**
     * 저장된 조회 조건을 한 번만 읽어 초기값으로 삼는다. 이후로는 화면 조작이 원본이다.
     *
     * 저장 값을 계속 관찰하지 않는 이유는, 조작할 때마다 저장소를 한 바퀴 돌고 와야
     * 화면이 바뀌게 되어 반응이 굼떠지기 때문이다.
     *
     * '몇 칸 전인지'는 복원하지 않는다. 항상 현재 칸에서 시작한다.
     */
    private fun restoreLastViewedConditions() {
        viewModelScope.launch(coroutineFailureLogger) {
            selection.value = PeriodSelection(usageViewPreferences.periodUnit.first())
            sortOrder.value = usageViewPreferences.sortOrder.first()
        }
    }

    private fun initialUiState() = UsageSummaryUiState(
        period = UsagePeriod.of(PeriodSelection.DEFAULT, LocalDate.now(clock))
    )

    private fun buildQuery(
        selection: PeriodSelection,
        sortOrder: UsageSortOrder,
        earliestRecordedDate: LocalDate?
    ): PeriodQuery {
        val today = LocalDate.now(clock)

        return PeriodQuery(
            selection = selection,
            period = UsagePeriod.of(selection, today),
            sortOrder = sortOrder,
            canGoPrevious = canGoPrevious(selection, today, earliestRecordedDate),
            canGoNext = selection.isNavigable && !selection.isCurrent
        )
    }

    /**
     * 한 칸 더 과거로 갔을 때 기록이 남아 있는지 확인한다.
     *
     * 비어 있는 기간을 끝없이 넘기게 두면 사용자는 어디가 끝인지 알 수 없다.
     * 저장된 가장 오래된 날짜에 걸치는 칸까지만 허용한다.
     */
    private fun canGoPrevious(
        selection: PeriodSelection,
        today: LocalDate,
        earliestRecordedDate: LocalDate?
    ): Boolean {
        if (!selection.isNavigable || earliestRecordedDate == null) {
            return false
        }

        val previousPeriod = UsagePeriod.of(selection.previous(), today)
        return !previousPeriod.endDateInclusive.isBefore(earliestRecordedDate)
    }

    /** 목록을 조회하기 위해 확정된 조건. 목록이 도착하면 그대로 화면 상태가 된다. */
    private data class PeriodQuery(
        val selection: PeriodSelection,
        val period: UsagePeriod,
        val sortOrder: UsageSortOrder,
        val canGoPrevious: Boolean,
        val canGoNext: Boolean
    ) {
        fun toUiState(appUsages: List<AppUsage>, isLoading: Boolean) = UsageSummaryUiState(
            selection = selection,
            period = period,
            sortOrder = sortOrder,
            appUsages = appUsages,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            isLoading = isLoading
        )
    }
}
