package com.dksystems.tracephone.domain.model

/**
 * 수신/송신 바이트를 함께 담는 값 객체.
 *
 * 두 값을 따로 들고 다니면 더할 때마다 한쪽을 빠뜨리기 쉽다.
 * 하나의 타입으로 묶고 [plus]를 제공해 합산 실수를 구조적으로 막는다.
 *
 * @property receivedBytes 내려받은 바이트
 * @property transmittedBytes 올려보낸 바이트
 */
data class UsageBytes(
    val receivedBytes: Long,
    val transmittedBytes: Long
) {

    /** 수신과 송신을 합친 총량. 목록 정렬과 요약 표시에 사용한다. */
    val totalBytes: Long
        get() = receivedBytes + transmittedBytes

    operator fun plus(other: UsageBytes): UsageBytes = UsageBytes(
        receivedBytes = receivedBytes + other.receivedBytes,
        transmittedBytes = transmittedBytes + other.transmittedBytes
    )

    companion object {
        val ZERO = UsageBytes(receivedBytes = 0L, transmittedBytes = 0L)
    }
}
