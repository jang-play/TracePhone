package com.dksystems.tracephone.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.domain.model.BillingCycleHistory
import com.dksystems.tracephone.domain.usecase.GetBillingCycleHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 주기 이력 화면의 상태.
 *
 * @property history 한도를 쓰지 않는 상태면 null. 그때는 안내 문구만 보여준다.
 */
data class BillingCycleHistoryUiState(
    val history: BillingCycleHistory? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class BillingCycleHistoryViewModel @Inject constructor(
    private val getBillingCycleHistory: GetBillingCycleHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingCycleHistoryUiState())
    val uiState: StateFlow<BillingCycleHistoryUiState> = _uiState.asStateFlow()

    init {
        // Flow가 아니라 한 번 읽어 담는다. 끝난 주기의 값은 더 이상 변하지 않고,
        // 한도를 바꾸는 곳은 설정 화면이라 이 화면이 열려 있는 동안 기준선이 낡을 경로가 없다.
        // 화면을 다시 들어오면 그때 값으로 다시 읽는다.
        viewModelScope.launch {
            _uiState.value = BillingCycleHistoryUiState(
                history = getBillingCycleHistory(),
                isLoading = false
            )
        }
    }
}
