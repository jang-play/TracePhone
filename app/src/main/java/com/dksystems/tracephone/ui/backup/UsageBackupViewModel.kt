package com.dksystems.tracephone.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.dksystems.tracephone.data.backup.UsageBackupStore
import com.dksystems.tracephone.domain.model.UsageBackupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 백업 화면의 상태.
 *
 * 상태를 스스로 들지 않고 [UsageBackupStore]에 위임한다. 내보내기는 몇 초가 걸릴 수 있고,
 * 그 사이에 설정 화면을 벗어나도 끊기지 않아야 한다.
 */
@HiltViewModel
class UsageBackupViewModel @Inject constructor(
    private val usageBackupStore: UsageBackupStore,
    private val clock: Clock
) : ViewModel() {

    val state: StateFlow<UsageBackupState> = usageBackupStore.state

    /**
     * 파일 선택 화면에 미리 적어 줄 이름.
     *
     * 날짜를 넣는 이유는 백업을 여러 개 쌓아 두게 되기 때문이다. 같은 이름이면 어느 것이
     * 최신인지 파일 목록만 보고 알 수 없다.
     */
    fun suggestedFileName(): String = "tracephone-usage-${LocalDate.now(clock)}.csv"

    fun exportTo(target: Uri) = usageBackupStore.exportTo(target)

    fun importFrom(source: Uri) = usageBackupStore.importFrom(source)
}
