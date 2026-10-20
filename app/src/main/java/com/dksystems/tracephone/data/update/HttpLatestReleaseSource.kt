package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.BuildConfig
import com.dksystems.tracephone.di.IoDispatcher
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.update.LatestReleaseSource
import com.dksystems.tracephone.domain.update.UpdateManifestFormatException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTPS_SCHEME = "https://"
private const val CONNECT_TIMEOUT_MILLIS = 5_000
private const val READ_TIMEOUT_MILLIS = 5_000

/**
 * 받아들일 응답의 최대 크기.
 *
 * 정상 응답은 200바이트가 되지 않는다. 주소가 엉뚱한 곳을 가리켜 거대한 문서가
 * 돌아오는 경우에 메모리를 끝까지 쓰지 않도록 상한을 둔다.
 */
private const val MAX_BODY_BYTES = 8 * 1024

/**
 * 최신 버전 기록을 HTTPS로 한 번 읽어 온다.
 *
 * ### Firebase SDK를 쓰지 않는다
 * Firebase Realtime Database는 경로 뒤에 `.json`을 붙이면 **평범한 HTTPS GET**으로
 * 읽힌다. 그래서 SDK도 `google-services.json`도 플러그인도 없다. 이 앱에 들어오는
 * 네트워크 코드는 이 파일 하나이고, 보내는 것은 요청 주소뿐이다.
 *
 * ### 캐시를 믿지 않는다
 * 중간 캐시가 옛 응답을 돌려주면 "최신입니다"라고 거짓을 말하게 된다.
 * 사용자가 확인을 누르는 것은 지금 상태를 묻는 것이므로 캐시를 끈다.
 */
@Singleton
class HttpLatestReleaseSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LatestReleaseSource {

    private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL

    // http로 적힌 주소는 설정되지 않은 것으로 본다. 평문으로 받은 주소는 바꿔치기할 수 있다.
    override val isConfigured: Boolean = manifestUrl.startsWith(HTTPS_SCHEME)

    override suspend fun fetchLatestRelease(): AppRelease = withContext(ioDispatcher) {
        parseUpdateManifest(readBody(URL(manifestUrl)))
    }

    private fun readBody(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.applyRequestSettings()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("응답 코드가 ${connection.responseCode} 이다: $url")
            }
            return connection.readLimitedBody()
        } finally {
            connection.disconnect()
        }
    }
}

private fun HttpURLConnection.applyRequestSettings() {
    requestMethod = "GET"
    connectTimeout = CONNECT_TIMEOUT_MILLIS
    readTimeout = READ_TIMEOUT_MILLIS
    useCaches = false
    setRequestProperty("Accept", "application/json")
}

/**
 * 상한까지만 읽는다.
 *
 * 상한을 넘기면 자르지 않고 형식 오류로 처리한다. 잘린 JSON을 파서에 넘기면
 * "형식이 틀렸다"와 "너무 크다"를 구별할 수 없게 된다.
 */
private fun HttpURLConnection.readLimitedBody(): String {
    // 상한보다 한 바이트 더 받을 수 있게 해 두면, 다 읽어 본 뒤 "넘쳤다"를 알 수 있다.
    val buffer = ByteArray(MAX_BODY_BYTES + 1)
    val filled = inputStream.use { stream -> stream.fill(buffer) }

    // 디코딩 전 바이트 수로 잰다. 디코딩한 문자열을 다시 인코딩해 재면, 잘린 UTF-8
    // 꼬리가 치환 문자로 바뀌면서 길이가 원본과 달라진다.
    if (filled > MAX_BODY_BYTES) {
        throw UpdateManifestFormatException("응답이 ${MAX_BODY_BYTES}바이트를 넘는다")
    }
    return String(buffer, 0, filled, Charsets.UTF_8)
}

/** 버퍼가 차거나 스트림이 끝날 때까지 읽고, 읽은 바이트 수를 돌려준다. */
private fun java.io.InputStream.fill(buffer: ByteArray): Int {
    var filled = 0
    while (filled < buffer.size) {
        val read = read(buffer, filled, buffer.size - filled)
        if (read == -1) break
        filled += read
    }
    return filled
}
