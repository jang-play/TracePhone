package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.update.UpdateManifestFormatException
import org.json.JSONException
import org.json.JSONObject

private const val FIELD_VERSION_CODE = "versionCode"
private const val FIELD_VERSION_NAME = "versionName"
private const val FIELD_DOWNLOAD_URL = "downloadUrl"

/**
 * 버전 기록을 담은 응답을 [AppRelease]로 바꾼다.
 *
 * 이 응답은 **사람이 Firebase 콘솔에 손으로 적어 넣은 것**이다. 오타, 빠뜨린 항목,
 * 숫자여야 할 자리의 문자열이 모두 현실적으로 일어난다. 그래서 하나하나 확인하고
 * 어긋나면 [UpdateManifestFormatException]을 던진다. 조용히 기본값으로 메우면
 * "최신입니다"라고 거짓을 말하게 된다.
 *
 * 비어 있는 경로를 읽으면 Firebase는 `null`을 돌려준다. 그 경우도 형식 오류로 본다.
 */
internal fun parseUpdateManifest(body: String): AppRelease {
    val root = try {
        JSONObject(body)
    } catch (error: JSONException) {
        throw UpdateManifestFormatException(
            "JSON 객체가 아니다 (길이 ${body.length}자): ${error.message}"
        )
    }

    return AppRelease(
        versionCode = root.requireVersionCode(),
        versionName = root.requireText(FIELD_VERSION_NAME),
        downloadUrl = root.requireText(FIELD_DOWNLOAD_URL)
    )
}

/**
 * `versionCode`는 1 이상의 정수여야 한다.
 *
 * `getInt`는 `"2"` 같은 문자열도 받아 준다. 콘솔에서 숫자를 문자열로 입력하는 일이
 * 흔하므로 그 관용은 남겨 두고, 숫자로 바뀌지 않는 경우만 걸러낸다.
 */
private fun JSONObject.requireVersionCode(): Int {
    val value = try {
        getInt(FIELD_VERSION_CODE)
    } catch (error: JSONException) {
        throw UpdateManifestFormatException("$FIELD_VERSION_CODE 가 정수가 아니다: ${error.message}")
    }

    if (value < 1) {
        throw UpdateManifestFormatException("$FIELD_VERSION_CODE 가 1보다 작다: $value")
    }
    return value
}

private fun JSONObject.requireText(field: String): String {
    val value = optString(field).trim()
    if (value.isEmpty()) {
        throw UpdateManifestFormatException("$field 가 비어 있다")
    }
    return value
}
