package com.dksystems.tracephone.domain.model

/**
 * 새 버전 APK를 받아 설치하기까지의 상태.
 *
 * 다섯 갈래인 이유는 화면이 각 경우에 **다른 말과 다른 버튼**을 내놓아야 하기 때문이다.
 * 특히 [InstallPermissionRequired]를 실패로 뭉개면, 사용자가 해야 할 일(설정에서 허용)이
 * "다시 눌러 보세요"로 바뀌어 아무리 눌러도 같은 자리를 돈다.
 */
sealed interface ApkDownload {

    /** 아직 아무 일도 하지 않았다. */
    data object Idle : ApkDownload

    /**
     * 받고 있다.
     *
     * @property totalBytes 서버가 길이를 알려주지 않으면 null이다. 그때는 진행률을 그릴 수 없다.
     */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ApkDownload

    /** 다 받아 설치 화면을 띄웠다. */
    data class Ready(val versionCode: Int) : ApkDownload

    /** 이 앱에 '출처를 알 수 없는 앱 설치'가 허용되지 않았다. 허용 없이는 설치 화면이 뜨지 않는다. */
    data object InstallPermissionRequired : ApkDownload

    data class Failed(val reason: ApkDownloadFailure) : ApkDownload
}

/**
 * 받기가 실패한 이유.
 *
 * 사용자가 할 수 있는 일이 서로 달라 구분한다. [NETWORK]만 다시 눌러 볼 가치가 있다.
 */
enum class ApkDownloadFailure {

    /** 연결하지 못했거나 응답이 끊겼다. */
    NETWORK,

    /** 받은 것이 APK라고 보기엔 너무 크다. 주소가 엉뚱한 곳을 가리키는 경우다. */
    TOO_LARGE,

    /** 저장할 자리를 만들지 못했다. */
    STORAGE
}

/**
 * 진행률. 그릴 수 없으면 null.
 *
 * 서버가 길이를 주지 않거나(총량 null) 0이라고 말하면 비율을 낼 수 없다. 그때 0%를 그리면
 * "멈췄다"로 읽히므로 아예 비율 없이 "받는 중"만 보여준다.
 *
 * 받은 양이 총량을 넘어가는 경우도 있다(전송 인코딩에 따라). 1을 넘겨 그리면 막대가
 * 삐져나가므로 범위를 자른다.
 */
fun downloadProgressOf(downloadedBytes: Long, totalBytes: Long?): Float? {
    if (totalBytes == null || totalBytes <= 0L) return null
    return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

/** 받은 것이 APK 상한을 넘었다. */
class ApkTooLargeException(message: String) : Exception(message)

/** 저장할 자리를 만들지 못했다. */
class ApkStorageException(message: String) : Exception(message)
