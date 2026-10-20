package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.di.ApplicationScope
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.model.UpdateCheckFailure
import com.dksystems.tracephone.domain.usecase.CheckForUpdateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "지금 새 버전이 있는가"의 단일 진실 원천.
 *
 * ### 왜 화면이 아니라 여기에 두는가
 * 요약 화면의 배너와 설정 화면의 카드가 같은 사실을 말해야 한다. 화면마다 확인하면
 * 앱을 한 번 쓰는 동안 같은 요청이 여러 번 나가고, 두 화면이 서로 다른 답을 보일 수 있다.
 *
 * ### 왜 주기 확인이 아니라 실행당 한 번인가
 * 주기 확인은 **앱이 열려 있지 않을 때도 네트워크를 쓴다.** 이 앱은 사용 기록을 다루므로
 * 통신이 사용자가 볼 수 있는 때에만 일어나는 성질을 지키는 쪽을 골랐다.
 * 실행당 한 번이면 저장할 시각도 필요 없다 — 응답이 100바이트대라 사실상 공짜다.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val checkForUpdate: CheckForUpdateUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    /** 이 빌드가 업데이트 확인을 지원하는가. 거짓이면 화면이 배너도 카드도 내보이지 않는다. */
    val isSupported: Boolean get() = checkForUpdate.isSupported

    private val mutableUpdateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.NotChecked)
    val updateCheck: StateFlow<UpdateCheck> = mutableUpdateCheck.asStateFlow()

    private val mutableDismissedVersionCode = MutableStateFlow<Int?>(null)

    /**
     * 사용자가 배너를 닫은 버전.
     *
     * 저장하지 않는다. 앱을 다시 켜면 다시 알린다 — 업데이트 안내는 한 번 닫았다고
     * 사라져야 하는 종류의 알림이 아니고, 저장하면 '어느 버전까지 닫았나'를 관리할
     * DataStore가 하나 더 생긴다.
     */
    val dismissedVersionCode: StateFlow<Int?> = mutableDismissedVersionCode.asStateFlow()

    /** 앱을 켠 뒤 처음 불릴 때만 실제로 확인한다. 화면이 다시 만들어져도 다시 묻지 않는다. */
    fun checkOnce() {
        if (mutableUpdateCheck.value is UpdateCheck.NotChecked) {
            check()
        }
    }

    /** 사용자가 '다시 확인'을 눌렀을 때. */
    fun recheck() = check()

    fun dismiss(versionCode: Int) {
        mutableDismissedVersionCode.value = versionCode
    }

    private fun check() {
        if (mutableUpdateCheck.value is UpdateCheck.Checking) return
        mutableUpdateCheck.value = UpdateCheck.Checking

        applicationScope.launch {
            mutableUpdateCheck.value = try {
                checkForUpdate()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                // 이 스코프에는 예외 처리기가 없다. 여기서 받지 않으면 앱이 죽는다.
                // 유스케이스가 연결 실패와 형식 오류를 이미 다루므로 여기 오는 것은
                // 예상하지 못한 종류다. 그래서 네트워크·형식으로 둘러대지 않고 따로 둔다.
                AppLog.error("Update check failed unexpectedly", unexpected)
                UpdateCheck.Failed(UpdateCheckFailure.UNKNOWN)
            }
        }
    }
}
