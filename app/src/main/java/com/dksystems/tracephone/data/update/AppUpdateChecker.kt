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
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 같은 확인을 다시 하기까지 기다리는 시간.
 *
 * 배포 간격은 며칠이지만 이 값을 거기에 맞추면, 버전을 올린 직후에 직접 확인해 보려는
 * 사람이 한참을 기다리게 된다. 화면 회전이나 앱 전환으로 요청이 쌓이는 것만 막을 만큼
 * 짧게 둔다. 더 급하면 설정 화면의 '다시 확인'이 시간과 무관하게 바로 묻는다.
 */
private val RECHECK_INTERVAL: Duration = Duration.ofMinutes(10)

/**
 * "지금 새 버전이 있는가"의 단일 진실 원천.
 *
 * ### 왜 화면이 아니라 여기에 두는가
 * 요약 화면의 배너와 설정 화면의 카드가 같은 사실을 말해야 한다. 화면마다 확인하면
 * 앱을 한 번 쓰는 동안 같은 요청이 여러 번 나가고, 두 화면이 서로 다른 답을 보일 수 있다.
 *
 * ### 왜 주기 확인이 아니라 앱이 앞으로 올 때인가
 * 주기 확인은 **앱이 열려 있지 않을 때도 네트워크를 쓴다.** 이 앱은 사용 기록을 다루므로
 * 통신이 사용자가 볼 수 있는 때에만 일어나는 성질을 지키는 쪽을 골랐다.
 * 응답이 100바이트대라 앞으로 올 때마다 물어도 사실상 공짜다.
 *
 * 묻는 자리는 [com.dksystems.tracephone.ui.TracePhoneApp] 한 곳이다. 화면마다 걸면
 * 어느 화면을 거쳤는지에 따라 확인 여부가 갈리고, 같은 요청이 여러 번 나간다.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    /** 이 빌드가 업데이트 확인을 지원하는가. 거짓이면 화면이 배너도 카드도 내보이지 않는다. */
    val isSupported: Boolean get() = checkForUpdate.isSupported

    /**
     * 마지막으로 **물어본** 시각. 성공한 시각이 아니다.
     *
     * 저장하지 않는다. 앱을 새로 켜면 곧바로 한 번 묻는 것이 맞고, 저장하면 DataStore가
     * 하나 더 생긴다. 메인 스레드에서만 읽고 쓴다(앞으로 오는 사건과 버튼 둘 다 그렇다).
     */
    private var lastCheckedAtMillis: Long? = null

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

    /**
     * 확인한 지 오래됐으면 다시 확인한다. 앱이 앞으로 올 때마다 불린다.
     *
     * **'한 번도 확인하지 않았는가'로 판단하지 않는다.** 그렇게 두면 프로세스가 사는 동안
     * 결과가 영구히 굳는다. 안드로이드는 프로세스를 잘 죽이지 않으므로 홈을 누르고 돌아온
     * 사용자는 영원히 같은 답을 보게 되고, 기록이 그 사이에 바뀌어도 알 수 없다.
     * 네트워크가 잠깐 없을 때 켰다면 실패가 굳어 **조용히 아무 일도 일어나지 않는다.**
     */
    fun checkIfStale() {
        if (isFresh()) return
        check()
    }

    /** 사용자가 '다시 확인'을 눌렀을 때. 시간이 얼마나 지났든 무조건 묻는다. */
    fun recheck() = check()

    fun dismiss(versionCode: Int) {
        mutableDismissedVersionCode.value = versionCode
    }

    /**
     * 방금 확인했는가.
     *
     * 앞으로 오는 일은 화면 회전이나 앱 전환으로도 일어나므로, 그때마다 묻게 두면 요청이
     * 쌓인다. **사용량을 재는 앱이 자기 통신을 늘리는 것**은 특히 피해야 한다.
     */
    private fun isFresh(): Boolean {
        val checkedAt = lastCheckedAtMillis ?: return false
        val elapsed = Duration.ofMillis(clock.millis() - checkedAt)

        // 기기 시간을 되돌리면 음수가 된다. 그때는 낡은 것으로 보고 다시 묻는다.
        return !elapsed.isNegative && elapsed < RECHECK_INTERVAL
    }

    private fun check() {
        if (mutableUpdateCheck.value is UpdateCheck.Checking) return

        // 결과가 아니라 **시도**를 기록한다. 실패했을 때 앞으로 올 때마다 다시 두드리면
        // 네트워크가 죽은 동안 요청만 쌓인다.
        lastCheckedAtMillis = clock.millis()
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
