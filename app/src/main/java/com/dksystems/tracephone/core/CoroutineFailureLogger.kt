package com.dksystems.tracephone.core

import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 어디서도 받지 못한 코루틴 예외를 로그로 남기고 앱을 살려 둔다.
 *
 * ### 왜 필요한가
 * `SupervisorJob`은 **형제 작업이 함께 죽는 것만** 막는다. 처리기가 없으면 잡히지 않은 예외는
 * 안드로이드의 기본 처리기까지 올라가 **프로세스를 죽인다.** 설정을 저장하다, 또는 앱을 켜다가
 * 앱이 사라진다.
 *
 * 실제로 닿는 경로가 있다. 저장 공간이 꽉 차거나 설정 파일이 깨지면 DataStore의 `edit`이
 * `IOException`을 던지고, DB가 손상되면 Room 조회가 `SQLiteException`을 던진다.
 * 읽기 쪽은 [com.dksystems.tracephone.data.preferences.recoveringFromReadFailure]가 막아
 * 주지만 쓰기 쪽에는 그런 그물이 없다.
 *
 * ### 왜 `launch`마다 `try/catch`를 두지 않는가
 * 지금 그런 자리가 열두 곳이 넘고, 하나를 빠뜨리면 그 자리만 앱을 죽인다. 빠뜨렸는지는
 * 컴파일러도 테스트도 알려 주지 않는다. 스코프를 만드는 자리에 한 번 걸어 두면
 * **앞으로 생길 자리까지** 덮는다.
 *
 * ### 왜 삼켜도 되는가
 * 이 처리기가 덮는 일은 설정 저장·판정·위젯 갱신처럼 **결과를 사용자에게 알리지 않는**
 * 뒷일이다. 알릴 것이 없는 실패로 앱을 죽이는 것보다, 로그를 남기고 다음 주기에 다시
 * 시도하는 쪽이 낫다. 사용자에게 보여야 하는 실패는 각 호출부가 자기 상태로 다룬다
 * (예: [com.dksystems.tracephone.data.update.AppUpdateDownloader]).
 */
val coroutineFailureLogger = CoroutineExceptionHandler { _, failure ->
    AppLog.error(
        "Coroutine failed with nothing to catch it; 이 작업만 포기하고 앱은 계속 돕니다. " +
            "다음 주기나 다음 조작에서 다시 시도됩니다.",
        failure
    )
}
