package com.dksystems.tracephone.data.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dksystems.tracephone.core.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** 매니페스트에 선언한 FileProvider의 authorities와 같아야 한다. */
private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

/**
 * 받은 APK를 시스템 설치 화면에 넘긴다.
 *
 * ### 서명을 검사하지 않는다
 * **안드로이드가 서명이 다른 APK의 설치를 거부한다.** 그 거부가 누적 사용 기록을 지키는
 * 마지막 방어선이고, 앱이 같은 검사를 한 번 더 하면 두 판단이 어긋날 수 있다.
 * 신뢰의 출발점은 받기 전에 주소가 `https://github.com/`인지 보는 쪽이다.
 *
 * ### 조용히 설치할 수는 없다
 * 안드로이드는 반드시 설치 화면을 띄워 사용자에게 확인을 받는다. 이 클래스가 할 수 있는
 * 일은 그 화면까지 데려가는 것까지다.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 이 앱이 설치 화면을 띄울 수 있는가.
     *
     * 거짓이면 설치 인텐트가 조용히 무시된다. 사용자가 시스템 설정에서 이 앱에
     * '출처를 알 수 없는 앱 설치'를 허용해야 한다.
     */
    val canInstallPackages: Boolean
        get() = context.packageManager.canRequestPackageInstalls()

    /** 허용 화면을 연다. 이 앱 항목이 바로 열리도록 패키지를 함께 넘긴다. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startOrLog(intent, "설치 허용 화면을 열지 못했습니다.")
    }

    /**
     * 설치 화면을 띄운다.
     *
     * `FileProvider`를 거치는 이유는 안드로이드 7부터 `file://`를 다른 앱에 넘길 수 없기
     * 때문이다. 읽기 권한을 인텐트에 실어 보내 설치 프로그램만 이 파일을 보게 한다.
     */
    fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            apk
        )

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startOrLog(intent, "설치 화면을 열지 못했습니다: ${apk.name}")
    }

    /**
     * 화면을 열지 못해도 앱을 멈추지 않는다.
     *
     * 기기에 따라 해당 설정 화면이 없을 수 있고, 그때 던져지는 예외로 앱이 죽으면
     * 사용자는 업데이트 버튼을 눌렀다가 앱을 잃는다.
     */
    private fun startOrLog(intent: Intent, failureMessage: String) {
        try {
            context.startActivity(intent)
        } catch (notFound: Exception) {
            AppLog.error(failureMessage, notFound)
        }
    }
}
