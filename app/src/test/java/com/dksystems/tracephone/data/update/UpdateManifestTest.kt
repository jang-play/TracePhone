package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.domain.update.UpdateManifestFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 버전 기록 파싱.
 *
 * 이 응답은 사람이 Firebase 콘솔에 손으로 적는다. 그래서 "정상"보다 **틀린 입력**을
 * 더 많이 고정한다. 조용히 기본값으로 메우면 앱이 "최신입니다"라고 거짓을 말한다.
 */
class UpdateManifestTest {

    @Test
    fun `세 항목이 모두 있으면 읽는다`() {
        val release = parseUpdateManifest(
            """{"versionCode":2,"versionName":"0.2.0","downloadUrl":"https://github.com/a/b/releases"}"""
        )

        assertEquals(2, release.versionCode)
        assertEquals("0.2.0", release.versionName)
        assertEquals("https://github.com/a/b/releases", release.downloadUrl)
    }

    @Test
    fun `모르는 항목이 더 있어도 읽는다`() {
        val release = parseUpdateManifest(
            """{"versionCode":3,"versionName":"0.3.0","downloadUrl":"https://x","notes":"설명"}"""
        )

        assertEquals(3, release.versionCode)
    }

    @Test
    fun `versionCode를 문자열로 적어도 읽는다`() {
        // 콘솔에서 숫자를 문자열로 넣는 일이 흔하다. 숫자로 바뀌는 한 받아 준다.
        val release = parseUpdateManifest(
            """{"versionCode":"4","versionName":"0.4.0","downloadUrl":"https://x"}"""
        )

        assertEquals(4, release.versionCode)
    }

    @Test
    fun `앞뒤 공백은 떼어 낸다`() {
        val release = parseUpdateManifest(
            """{"versionCode":2,"versionName":"  0.2.0  ","downloadUrl":" https://x "}"""
        )

        assertEquals("0.2.0", release.versionName)
        assertEquals("https://x", release.downloadUrl)
    }

    @Test
    fun `JSON이 아니면 형식 오류다`() {
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("<html>404</html>")
        }
    }

    @Test
    fun `빈 경로를 읽어 null이 오면 형식 오류다`() {
        // Firebase는 아무것도 없는 경로에 대해 문자열 "null"을 돌려준다.
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("null")
        }
    }

    @Test
    fun `versionCode가 없으면 형식 오류다`() {
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("""{"versionName":"0.2.0","downloadUrl":"https://x"}""")
        }
    }

    @Test
    fun `versionCode가 숫자가 아니면 형식 오류다`() {
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("""{"versionCode":"이번것","versionName":"a","downloadUrl":"https://x"}""")
        }
    }

    @Test
    fun `versionCode가 0이면 형식 오류다`() {
        // versionCode는 1부터 시작한다. 0이면 설치된 모든 버전보다 낮아 영원히 최신이 된다.
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("""{"versionCode":0,"versionName":"a","downloadUrl":"https://x"}""")
        }
    }

    @Test
    fun `versionName이 비면 형식 오류다`() {
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("""{"versionCode":2,"versionName":"   ","downloadUrl":"https://x"}""")
        }
    }

    @Test
    fun `downloadUrl이 없으면 형식 오류다`() {
        assertThrows(UpdateManifestFormatException::class.java) {
            parseUpdateManifest("""{"versionCode":2,"versionName":"0.2.0"}""")
        }
    }
}
