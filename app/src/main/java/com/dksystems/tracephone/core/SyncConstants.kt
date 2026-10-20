package com.dksystems.tracephone.core

/**
 * 동기화 주기와 수집 범위를 정의하는 상수.
 *
 * 이 값들을 코드에 직접 쓰지 않고 반드시 여기를 참조한다.
 * 튜닝이 필요할 때 고칠 곳이 한 군데로 모인다.
 */
object SyncConstants {

    /** 주기 동기화 간격(시간). WorkManager의 최소 주기(15분)보다 충분히 크다. */
    const val SYNC_INTERVAL_HOURS = 6L

    /**
     * 매 동기화마다 다시 조회해 덮어쓸 최근 일수.
     *
     * 오늘 하루만 갱신하면 기기가 며칠 꺼져 있었을 때 구멍이 남는다.
     * 최근 며칠을 항상 다시 훑어 구멍을 자동으로 메운다.
     */
    const val RECENT_BACKFILL_DAYS = 7L

    /**
     * 최초 실행 시 긁어올 최대 과거 일수.
     *
     * 시스템의 상세 통계 보관 기간이 기기별로 다르므로, 없는 날짜는 0이 반환된다.
     * 별도 예외 처리 없이 그대로 저장하면 된다.
     */
    const val INITIAL_BACKFILL_DAYS = 90L

    /**
     * 사용량 경고를 판정하는 기본 주기(분). **안전망이자 하한이다.**
     *
     * `PeriodicWorkRequest`가 허용하는 최소 주기가 15분이라 이보다 짧게 잡을 수 없다.
     * 배터리 최적화 예외가 있으면 [FAST_ALERT_CHECK_INTERVAL_MINUTES] 쪽이 더 촘촘히 돌고,
     * 이 주기는 그 연쇄가 끊겼을 때 되살리는 역할을 맡는다.
     */
    const val ALERT_CHECK_INTERVAL_MINUTES = 15L

    /**
     * 배터리 최적화 예외일 때 쓰는 빠른 판정 주기(분).
     *
     * 15분 하한은 `PeriodicWorkRequest`에만 걸린다. `OneTimeWorkRequest`가 스스로를 다시 걸면
     * 그보다 짧게 돌 수 있다. 다만 **예외가 있을 때만 그렇다.**
     * 예외가 없으면 App Standby Bucket의 세션 상한을 몇 분 만에 태우고 나머지 시간을 통째로
     * 막히므로, 5분을 노리다가 오히려 15분보다 나빠진다.
     */
    const val FAST_ALERT_CHECK_INTERVAL_MINUTES = 5L

    /**
     * 오늘치 시스템 조회 결과를 다시 쓰는 시간(밀리초).
     *
     * [com.dksystems.tracephone.work.UsageAlertWorker] 한 번이 경고 판정 · 한도 판정 · 위젯 갱신을
     * 마치는 데 걸리는 시간보다 넉넉히 길고, 시스템이 네트워크 통계를 실제로 갱신하는 간격보다는 짧다.
     * 이 창 안에서 다시 물어도 어차피 같은 숫자가 돌아오므로 화면에 낡은 값이 보이지 않는다.
     *
     * 줄이는 것은 정확도가 아니라 **의미 없는 재질문**이다.
     */
    const val TODAY_USAGE_MEMO_TTL_MILLIS = 30_000L
}

/** WorkManager에 등록하는 작업의 고유 이름. */
object WorkNames {

    /** 6시간 주기 동기화 작업 */
    const val PERIODIC_SYNC = "tracephone.periodic_usage_sync"

    /** 최초 백필 및 화면 진입 시 즉시 동기화 작업 */
    const val ONE_TIME_SYNC = "tracephone.one_time_usage_sync"

    /** 15분 주기 사용량 경고 판정 작업. 빠른 판정이 끊겼을 때 되살리는 안전망을 겸한다. */
    const val PERIODIC_ALERT_CHECK = "tracephone.periodic_usage_alert_check"

    /** 배터리 최적화 예외일 때 스스로를 다시 거는 빠른 판정 작업 */
    const val FAST_ALERT_CHECK = "tracephone.fast_usage_alert_check"
}

/** Room 데이터베이스 설정. */
object DatabaseConstants {

    const val DATABASE_NAME = "tracephone.db"
}
