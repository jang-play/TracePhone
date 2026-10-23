package com.dksystems.tracephone.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.AppListScale
import com.dksystems.tracephone.domain.model.dailyReferenceBytesOf
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.ui.update.AppUpdateBanner

/**
 * 앱별 데이터 사용량 목록 화면.
 *
 * 화면을 이루는 조각은 파일로 나눠 두었다 — 앱 바는 [PeriodTopBar], 선택 칩은
 * [PeriodUnitSelector]·[SortOrderSelector], 합계는 [UsageTotalsCard], 목록 한 줄은 [AppUsageRow].
 * 여기에는 **그것들을 어떤 순서로 쌓는지**만 남긴다.
 *
 * @param onAppSelected 앱을 선택했을 때 상세 화면으로 이동한다. uid와 현재 기간을 함께 넘긴다.
 * @param onAllAppsSelected 합계 카드를 눌렀을 때 이 기간의 모든 앱을 한 화면에서 본다.
 *   단위를 함께 넘긴다 - 날짜 범위만으로는 그쪽 제목에 기간을 어떻게 적을지 정할 수 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSummaryScreen(
    onAppSelected: (uid: Int, period: UsagePeriod) -> Unit,
    onSettingsSelected: () -> Unit,
    onCycleHistorySelected: () -> Unit,
    onAllAppsSelected: (period: UsagePeriod, unit: PeriodUnit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthlyLimitStatus by viewModel.monthlyLimitStatus.collectAsStateWithLifecycle()
    val appListLayout by viewModel.appListLayout.collectAsStateWithLifecycle()
    val syncAge by viewModel.syncAge.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    // 목록과 한도는 보는 기간이 달라 ViewModel에서 따로 흘러온다. 둘을 합쳐야 자가 정해지므로
    // 여기서 만든다. 어떻게 합칠지는 도메인의 순수 함수가 정하고, 화면은 값을 건네기만 한다.
    val rowContext = remember(uiState, appListLayout, monthlyLimitStatus) {
        AppUsageRowContext(
            sortOrder = uiState.sortOrder,
            layout = appListLayout,
            scale = AppListScale(
                maxBytes = uiState.mobileTotalBytes,
                referenceBytes = dailyReferenceBytesOf(
                    unit = uiState.selection.unit,
                    limit = monthlyLimitStatus
                )
            )
        )
    }

    // 앱 바와 목록이 같은 값을 쓴다. 화살표와 스와이프가 같은 일을 하게 하려면 한 곳에서 와야 한다.
    val navigation = PeriodNavigation(
        canGoPrevious = uiState.canGoPrevious,
        canGoNext = uiState.canGoNext,
        onPrevious = viewModel::showPreviousPeriod,
        onNext = viewModel::showNextPeriod,
        onReturnToCurrent = viewModel::showCurrentPeriod
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            PeriodTopBar(
                label = rememberPeriodLabel(uiState.selection.unit, uiState.period),
                navigation = navigation,
                onSettingsSelected = onSettingsSelected
            )
        }
    ) { innerPadding ->
        // 머리말은 제자리에 두고 **목록만** 움직인다.
        //
        // 한도·기간·합계는 목록을 읽는 동안 계속 견줘야 하는 값이다. 함께 스크롤되면
        // 어떤 앱이 많이 썼는지 보려고 내린 순간 '무엇에 견주는 중이었는지'가 사라진다.
        //
        // **대가는 목록이 짧아지는 것이다.** 머리말이 화면의 절반 가까이를 늘 차지하므로
        // 자세히 보기에서는 다섯 줄 남짓만 보인다. 더 보고 싶으면 설정에서 간단히 보기로 바꾼다.
        // 당김 새로고침은 **현재 기간에서만** 동작한다.
        // 지난 기간의 합계는 이미 저장된 기록이라 다시 물어도 같은 값이 나온다.
        //
        // 제스처는 **목록에서** 시작한다. 머리말은 스크롤되지 않아 당김을 전달하지 않는다.
        // 세로 당김과 기간 넘김은 방향이 달라(`periodSwipe`는 가로) 서로를 가로채지 않는다.
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                    enabled = uiState.selection.isCurrent,
                    onRefresh = viewModel::refresh
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .periodSwipe(navigation),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 새 버전이 있을 때만 나타난다. 앱을 켜면 가장 먼저 보이는 화면이라
                // "시작할 때 확인"의 자리가 여기다.
                AppUpdateBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // 과거 기간을 볼 때도 남겨 둔다. 이 카드가 답하는 질문("이번 달 요금제를
                // 넘기겠는가")은 사용자가 어느 날짜를 들여다보든 달라지지 않는다.
                //
                // 값은 늘 **이번 청구 주기**다. 화면의 기간 선택을 따라가지 않는다.
                // 카드가 스스로 `9월 1일 ~ 9월 30일`이라고 기간을 적으므로 위의 날짜와 헷갈리지 않는다.
                //
                // 지난 주기의 한도를 대신 보여주는 방법은 택하지 않았다. '남은 날수'와 '하루 권장량'이
                // 끝난 주기에서는 뜻이 없어, 카드의 말투 전체가 거짓이 된다.
                // 카드를 누르면 지난 주기들의 실적을 본다. 이 카드가 '이번 주기'만 말하므로,
                // "그럼 지난 달엔 얼마였나"는 바로 여기서 생기는 질문이다.
                monthlyLimitStatus?.let { status ->
                    MonthlyLimitProgressCard(
                        status = status,
                        onHistorySelected = onCycleHistorySelected,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                PeriodUnitSelector(
                    selected = uiState.selection.unit,
                    onSelect = viewModel::selectUnit,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // 확인 시각은 **현재 기간에서만** 적는다.
                //
                // 지난 달의 합계는 이미 확정된 기록이라 "언제 확인했나"가 답할 질문이 아니다.
                // 그 자리에 "3주 전에 확인함"이 뜨면 정보는 0인데 "앱이 3주 동안 멈췄나"라는
                // 불안만 남는다.
                UsageTotalsCard(
                    mobileBytes = uiState.mobileTotalBytes,
                    wifiBytes = uiState.wifiTotalBytes,
                    totalBytes = uiState.totalBytes,
                    syncAge = syncAge.takeIf { uiState.selection.isCurrent },
                    onAllAppsSelected = {
                        onAllAppsSelected(uiState.period, uiState.selection.unit)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 머리말이 고정되면서 `stickyHeader`가 필요 없어졌다.
                // 뒤에 바탕색을 깔던 것도 함께 지웠다 - 이제 목록이 이 줄 아래를 지나가지 않는다.
                SortOrderSelector(
                    selected = uiState.sortOrder,
                    onSelect = viewModel::selectSortOrder,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // 남는 자리를 전부 목록에 준다. 머리말이 화면보다 커지면 이 값이 0이 되므로
                // 머리말은 늘 한 화면 안에 들어가야 한다.
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    appUsageItems(
                        uiState = uiState,
                        rowContext = rowContext,
                        onAppSelected = { uid -> onAppSelected(uid, uiState.period) }
                    )
                }
            }

            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * 목록 본문. 비어 있거나 불러오는 중이면 그 사실을 한 칸으로 알린다.
 *
 * 화면 전체를 채우는 대신 정해진 높이의 칸을 쓴다.
 * `LazyColumn` 안에서는 높이가 정해지지 않은 칸이 들어가면 배치가 무너진다.
 */
private fun LazyListScope.appUsageItems(
    uiState: UsageSummaryUiState,
    rowContext: AppUsageRowContext,
    onAppSelected: (Int) -> Unit
) {
    when {
        uiState.isLoading -> item(key = MESSAGE_KEY) {
            MessageBox { CircularProgressIndicator() }
        }

        uiState.visibleAppUsages.isEmpty() -> item(key = MESSAGE_KEY) {
            MessageBox {
                Text(
                    text = stringResource(R.string.summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            items(items = uiState.visibleAppUsages, key = { it.app.uid }) { usage ->
                AppUsageRow(
                    usage = usage,
                    rowContext = rowContext,
                    onClick = { onAppSelected(usage.app.uid) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MESSAGE_BOX_HEIGHT),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

private val MESSAGE_BOX_HEIGHT = 160.dp

/** 안내 칸의 키. 불러오는 중과 비어 있음이 같은 자리를 쓴다. */
private const val MESSAGE_KEY = "list_message"
