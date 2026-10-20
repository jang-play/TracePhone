package com.dksystems.tracephone.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.dksystems.tracephone.core.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 홈 화면 위젯을 다시 그리게 한다.
 *
 * 위젯은 스스로 값을 다시 읽지 못하므로 값이 바뀔 만한 시점마다 이쪽에서 알려 줘야 한다.
 * 시스템의 주기 갱신(30분)만 믿으면 한도를 방금 바꿔도 한참 동안 옛 숫자가 남는다.
 *
 * 위젯이 홈 화면에 없을 수도 있고 갱신이 실패할 수도 있다.
 * 어느 쪽이든 앱 동작을 멈출 이유가 아니므로 로그만 남기고 넘어간다.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 갱신이 끝날 때까지 기다린다.
     *
     * 부르는 쪽은 화면과 함께 취소되지 않는 스코프에서 실행해야 한다.
     * 설정을 바꾸고 곧바로 뒤로 나가면 갱신이 중간에 끊겨 위젯에 옛 값이 남는다.
     */
    suspend fun refresh() {
        try {
            TracePhoneWidget().updateAll(context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (updateFailure: Exception) {
            AppLog.warn("Widget update failed; 다음 갱신에서 다시 시도합니다. ${updateFailure.message}")
        }
    }
}
