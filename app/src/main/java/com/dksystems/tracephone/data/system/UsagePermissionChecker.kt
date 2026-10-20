package com.dksystems.tracephone.data.system

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.domain.permission.UsageAccessPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용 정보 접근 권한이 실제로 켜져 있는지 AppOps로 확인한다.
 *
 * 매니페스트에 `PACKAGE_USAGE_STATS`를 선언해도 그것만으로는 부여되지 않는다.
 * 사용자가 설정에서 스위치를 켰는지는 AppOps의 `GET_USAGE_STATS` 항목으로만 알 수 있다.
 */
@Singleton
class UsagePermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageAccessPermission {

    override fun isGranted(): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        if (appOpsManager == null) {
            AppLog.error(
                "AppOpsManager unavailable at UsagePermissionChecker; " +
                    "treating usage access as denied. 기기 상태를 확인하세요."
            )
            return false
        }

        val mode = checkUsageStatsOp(appOpsManager)

        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true

            // MODE_DEFAULT는 "AppOps에 별도 설정이 없음"을 뜻한다.
            // 이때는 권한 자체가 부여되었는지로 최종 판단해야 한다.
            AppOpsManager.MODE_DEFAULT -> hasUsageStatsPermissionGrant()

            else -> false
        }
    }

    private fun checkUsageStatsOp(appOpsManager: AppOpsManager): Int =
        appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )

    private fun hasUsageStatsPermissionGrant(): Boolean =
        context.checkPermission(
            Manifest.permission.PACKAGE_USAGE_STATS,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
}
