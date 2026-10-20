package com.dksystems.tracephone.work

import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.di.ApplicationScope
import com.dksystems.tracephone.domain.model.UsageSyncResult
import com.dksystems.tracephone.domain.usecase.SyncUsageUseCase
import com.dksystems.tracephone.widget.WidgetRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자가 당겨서 요청한 즉시 새로고침.
 *
 * ### WorkManager를 거치지 않는 이유
 * [UsageSyncScheduler.requestImmediateSync]는 작업을 걸어 두고 곧바로 돌아온다. 당김
 * 새로고침은 **끝난 시점**을 알아야 표시를 끌 수 있는데, 걸어 둔 작업의 완료를 관찰하려면
 * '돌기 시작했다'를 먼저 붙잡아야 한다. 동기화가 그보다 빨리 끝나면 그 전이를 놓치고
 * 표시가 영원히 돌거나 즉시 꺼진다. 그래서 유스케이스를 직접 부르고 반환을 기다린다.
 *
 * 주기 동기화와 겹쳐 돌 수 있지만 저장은 업서트라 같은 값을 다시 써도 결과가 같다.
 *
 * ### 화면 스코프를 쓰지 않는 이유
 * 새로고침은 동기화와 위젯 갱신 **두 단계**다. 화면 스코프에서 돌리면 새로고침하고 곧바로
 * 뒤로 나갈 때 위젯 갱신이 끊겨, 화면은 새 값인데 위젯에는 옛 숫자가 남는다.
 * 같은 문제를 설정 화면에서 실기기로 겪었고 그래서 [ApplicationScope]가 있다.
 *
 * ### Singleton인 이유
 * '돌고 있는가'를 화면보다 오래 들고 있어야 한다. 화면이 다시 만들어져도 표시가 이어지고,
 * 연달아 당겨도 한 번만 돈다.
 */
@Singleton
class UsageRefresher @Inject constructor(
    private val syncUsage: SyncUsageUseCase,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val mutableIsRefreshing = MutableStateFlow(false)

    /** 지금 새로고침이 돌고 있는가. 당김 표시를 켜고 끄는 데 쓴다. */
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    /**
     * 한 번 새로고침한다. 이미 돌고 있으면 아무 일도 하지 않는다.
     *
     * 실패해도 표시는 끈다. 실패를 따로 알리지 않는 이유는 합계 카드의 '확인 시각'이
     * **성공했을 때만** 갱신되어 그 자체로 결과를 말하기 때문이다. 같은 사실을 두 곳에서
     * 말하면 한쪽이 먼저 낡는다.
     */
    fun refresh() {
        // 표시를 켜는 것과 돌고 있는지 확인하는 것을 한 번에 한다.
        // 따로 하면 빠르게 두 번 당길 때 둘 다 통과해 동기화가 두 번 돈다.
        if (!mutableIsRefreshing.compareAndSet(expect = false, update = true)) return

        applicationScope.launch {
            try {
                logOutcome(syncUsage())
                widgetRefresher.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                // 이 스코프에는 예외 처리기가 없다. 여기서 받지 않으면 앱이 죽는다.
                //
                // 동기화는 날짜별 조회 실패를 스스로 삼키지만 **저장은 그러지 않는다** —
                // `UsageSynchronizerImpl`의 `upsertAll`은 try 블록 밖에 있다.
                AppLog.error("Manual refresh failed unexpectedly", unexpected)
            } finally {
                mutableIsRefreshing.value = false
            }
        }
    }

    /**
     * 결과를 로그로만 남긴다. 화면에는 알리지 않는다.
     *
     * 당김 표시가 돌고 멈추는 것 자체가 사용자에게는 응답이고, 성공 여부는 합계 카드의
     * '확인 시각'이 말한다. 같은 사실을 세 곳에서 말하면 한 곳이 먼저 낡는다.
     *
     * 권한이 없는 경우는 이 화면에서 거의 닿지 않는다. 권한을 잃으면 앱이 권한 안내 화면으로
     * 바뀌기 때문이다. 그래도 분기를 빠뜨리지 않는다 — 빠뜨리면 아무 흔적도 남지 않는다.
     */
    private fun logOutcome(result: UsageSyncResult) {
        when (result) {
            is UsageSyncResult.Success -> Unit

            UsageSyncResult.PermissionRequired ->
                AppLog.info("Manual refresh skipped: 사용 정보 접근 권한이 없습니다.")

            is UsageSyncResult.Failure ->
                AppLog.error(
                    "Manual refresh failed; 다음 주기 동기화에서 다시 시도합니다.",
                    result.cause
                )
        }
    }
}
