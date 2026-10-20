package com.dksystems.tracephone.data.update

import android.content.Context
import com.dksystems.tracephone.di.IoDispatcher
import com.dksystems.tracephone.domain.model.ApkStorageException
import com.dksystems.tracephone.domain.model.ApkTooLargeException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 30_000
private const val COPY_BUFFER_BYTES = 64 * 1024

/**
 * 받아들일 APK의 최대 크기.
 *
 * 실측 2.5 MB다. 상한을 두는 것은 용량을 아끼려는 게 아니라, 주소가 엉뚱한 곳을 가리켜
 * 거대한 파일이 돌아올 때 저장 공간을 끝까지 쓰지 않으려는 것이다.
 */
private const val MAX_APK_BYTES = 64L * 1024 * 1024

/** 받은 APK를 두는 자리. `cacheDir` 아래라 시스템이 공간을 필요로 하면 지울 수 있다. */
private const val APK_CACHE_DIR = "apk"

/**
 * 새 버전 APK를 HTTPS로 받아 캐시에 둔다.
 *
 * ### 리다이렉트를 따라간다
 * `github.com/.../releases/download/...`는 `release-assets.githubusercontent.com`으로
 * 넘긴다. `HttpURLConnection`은 https→https 리다이렉트를 기본으로 따라가므로 그대로 둔다.
 * **https→http로는 따라가지 않는다** — 평문으로 내려가는 APK를 받는 일이 없다.
 *
 * ### 받기 전에 캐시를 비운다
 * 옛 버전 APK가 쌓이면 사용자 몰래 용량을 먹고, 설치 화면에 엉뚱한 파일을 넘길 위험이 생긴다.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * @param onProgress 받은 바이트와 총 바이트(모르면 null). 진행률을 그릴 쪽에서 쓴다.
     * @throws IOException 연결·응답 실패
     * @throws ApkTooLargeException [MAX_APK_BYTES]를 넘었다
     * @throws ApkStorageException 저장할 자리를 만들지 못했다
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): File = withContext(ioDispatcher) {
        val target = File(prepareCacheDir(), fileName)

        try {
            downloadTo(target, URL(url), onProgress)
        } catch (failure: Exception) {
            // 반쪽 파일을 남기면 다음에 그것을 설치 화면에 넘길 수 있다.
            target.delete()
            throw failure
        }

        target
    }

    private fun prepareCacheDir(): File {
        val dir = File(context.cacheDir, APK_CACHE_DIR)
        dir.listFiles()?.forEach { it.delete() }

        if (!dir.isDirectory && !dir.mkdirs()) {
            throw ApkStorageException("캐시 폴더를 만들지 못했다: $dir")
        }
        return dir
    }

    private fun downloadTo(
        target: File,
        url: URL,
        onProgress: (Long, Long?) -> Unit
    ) {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.applyRequestSettings()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("응답 코드가 ${connection.responseCode} 이다: $url")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            connection.inputStream.use { source ->
                target.outputStream().use { sink ->
                    copyWithLimit(source, sink, totalBytes, onProgress)
                }
            }
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
}

/**
 * 상한까지만 받아 옮긴다.
 *
 * 총량을 미리 알더라도 받은 양을 함께 센다. 서버가 알려준 길이와 실제로 내려오는 양이
 * 다를 수 있고, 믿는 쪽만 보면 상한이 무력해진다.
 */
private fun copyWithLimit(
    source: java.io.InputStream,
    sink: java.io.OutputStream,
    totalBytes: Long?,
    onProgress: (Long, Long?) -> Unit
) {
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var downloaded = 0L

    onProgress(0L, totalBytes)
    while (true) {
        val read = source.read(buffer)
        if (read == -1) break

        downloaded += read
        if (downloaded > MAX_APK_BYTES) {
            throw ApkTooLargeException("받은 양이 ${MAX_APK_BYTES}바이트를 넘었다")
        }

        sink.write(buffer, 0, read)
        onProgress(downloaded, totalBytes)
    }
}
