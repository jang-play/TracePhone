# Room이 생성하는 구현체와 엔티티는 리플렉션으로 접근되므로 유지한다.
-keep class com.dksystems.tracephone.data.local.** { *; }

# 도메인 모델은 디버깅 편의를 위해 이름을 유지한다.
-keepnames class com.dksystems.tracephone.domain.model.** { *; }

# 열거형 이름 문자열을 두 곳에 그대로 저장한다.
#   - daily_app_usage의 networkKind 열 (RoomTypeConverters.networkKindToText)
#   - DataStore의 테마·정렬·기간·위젯 색·알림 지점 값
# 이름이 바뀌면 저장된 값을 되읽지 못한다. 특히 DB 쪽은 시스템이 더 보관하지 않는
# 누적 기록이라 복구가 불가능하다. 지금 R8은 이름을 바꾸지 않지만 그 판단에 의존하지 않는다.
-keepclassmembers enum com.dksystems.tracephone.domain.model.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
