package com.dksystems.tracephone.data.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.dksystems.tracephone.core.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * 설정 파일을 읽지 못했을 때 기본값으로 이어 간다.
 *
 * **읽기 실패로 화면이나 기능이 멈추는 것이 더 나쁘다.** 설정 하나 때문에 목록이 뜨지 않거나
 * 위젯이 비어 보이면 사용자는 앱이 고장 났다고 여긴다. 빈 설정을 흘려 보내면
 * 각 저장소가 자기 기본값으로 되돌아가 하던 일을 계속한다.
 *
 * `IOException`만 삼킨다. 그 밖의 예외는 코드의 잘못이므로 조용히 덮으면
 * 원인을 찾을 기회를 잃는다.
 *
 * @param preferencesName 어느 설정 파일인지. 로그에서 저장소를 가려내는 데 쓴다.
 * @param fallbackDescription 대신 무엇으로 이어 가는지. 로그를 읽는 사람이 영향 범위를 알 수 있게 한다.
 */
internal fun Flow<Preferences>.recoveringFromReadFailure(
    preferencesName: String,
    fallbackDescription: String
): Flow<Preferences> = catch { error ->
    if (error is IOException) {
        AppLog.error("Reading $preferencesName failed; $fallbackDescription", error)
        emit(emptyPreferences())
    } else {
        throw error
    }
}
