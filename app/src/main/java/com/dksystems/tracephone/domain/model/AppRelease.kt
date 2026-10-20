package com.dksystems.tracephone.domain.model

/**
 * 배포된 최신 버전의 기록.
 *
 * 이 값은 **앱 바깥에서 사람이 적어 넣은 것**이다(Firebase 콘솔).
 * 그래서 신뢰할 수 있는 사실이 아니라 "누군가 적어 둔 주장"으로 다룬다.
 * 특히 [downloadUrl]은 검증 없이 열지 않는다. [UpdateCheck] 쪽 설명을 함께 본다.
 *
 * @property versionCode 설치 여부를 판단하는 숫자. 버전 비교의 유일한 기준이다.
 * @property versionName 사람이 읽는 이름. 비교에 쓰지 않는다(`0.10.0`과 `0.9.0`을
 *   문자열로 비교하면 뒤집히기 때문이다).
 * @property downloadUrl 내려받을 주소.
 */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

/**
 * 업데이트 확인의 결과.
 *
 * 성공/실패 두 갈래가 아니라 다섯 갈래다. 화면이 각 경우에 **다른 말**을 해야 하기
 * 때문이다. 특히 [Unavailable]과 [Failed]를 섞으면 "확인 기능이 없는 빌드"와
 * "확인했는데 실패함"이 같은 문구를 받게 되어 사용자가 다시 눌러 볼지 판단할 수 없다.
 */
sealed interface UpdateCheck {

    /** 확인 주소가 빌드에 들어 있지 않다. 화면에 기능을 아예 내보이지 않는다. */
    data object Unavailable : UpdateCheck

    /** 아직 확인하지 않았다. */
    data object NotChecked : UpdateCheck

    /** 확인 중. */
    data object Checking : UpdateCheck

    /** 설치된 것이 최신이거나 더 새롭다. */
    data class UpToDate(val installedVersionName: String) : UpdateCheck

    /** 더 새 버전이 있다. */
    data class Available(val release: AppRelease) : UpdateCheck

    /** 확인하지 못했다. */
    data class Failed(val reason: UpdateCheckFailure) : UpdateCheck
}

/**
 * 확인이 실패한 이유.
 *
 * 사용자가 할 수 있는 일이 서로 다르기 때문에 구분한다.
 * [NETWORK]는 다시 눌러 볼 가치가 있고, 나머지 둘은 다시 눌러도 같은 결과가 나온다.
 */
enum class UpdateCheckFailure {

    /** 연결하지 못했거나 응답이 오지 않았다. */
    NETWORK,

    /** 응답을 읽었으나 형식이 기대와 다르다. */
    MALFORMED,

    /** 내려받을 주소를 신뢰할 수 없다. 자세한 내용은 [AppRelease] 참조. */
    UNTRUSTED_DOWNLOAD_URL
}
