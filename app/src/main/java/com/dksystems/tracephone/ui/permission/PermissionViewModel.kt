package com.dksystems.tracephone.ui.permission

import androidx.lifecycle.ViewModel
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.domain.permission.UsageAccessPermission
import com.dksystems.tracephone.work.UsageSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 사용 정보 접근 권한 상태를 들고 있다.
 *
 * 이 권한은 앱이 요청할 수 없고 사용자가 설정 화면에서 켜야 한다.
 * 따라서 결과를 콜백으로 받을 방법이 없고, 화면이 다시 보일 때마다 직접 확인하는 수밖에 없다.
 */
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val usageAccessPermission: UsageAccessPermission,
    private val usageSyncScheduler: UsageSyncScheduler
) : ViewModel() {

    private val _isGranted = MutableStateFlow(usageAccessPermission.isGranted())
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    /**
     * 권한 상태를 다시 확인한다. 화면이 다시 보일 때마다 호출한다.
     *
     * 방금 허용된 경우에는 과거 기록을 한 번에 채우는 백필을 시작한다.
     * 시스템이 아직 들고 있는 과거를 놓치기 전에 확보해야 하기 때문이다.
     */
    fun refresh() {
        val granted = usageAccessPermission.isGranted()
        val justGranted = granted && !_isGranted.value

        _isGranted.value = granted

        if (justGranted) {
            AppLog.info("Usage access granted; 과거 기록 백필을 시작합니다.")
            usageSyncScheduler.requestImmediateSync(forceInitialBackfill = true)
        }
    }
}
