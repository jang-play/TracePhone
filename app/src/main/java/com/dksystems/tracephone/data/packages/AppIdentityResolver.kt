package com.dksystems.tracephone.data.packages

import android.app.usage.NetworkStats
import android.content.Context
import android.content.pm.PackageManager
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.domain.model.AppIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UID를 화면에 표시할 앱 이름으로 바꾼다.
 *
 * 매핑 실패는 예외 상황이 아니라 **정상적으로 일어나는 일**이다.
 * 제거된 앱, 테더링, 커널 트래픽 등이 통계에 섞여 들어오기 때문이다.
 * 따라서 이 클래스는 어떤 입력에도 예외를 던지지 않고 항상 표시 가능한 [AppIdentity]를 돌려준다.
 *
 * 조회 결과는 캐시한다. 목록에 수십~수백 개의 UID가 반복 등장하는데
 * 매번 `PackageManager`를 왕복하면 스크롤이 눈에 띄게 끊긴다.
 */
@Singleton
class AppIdentityResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageManager: PackageManager = context.packageManager

    /** UID -> 확정된 앱 정보. 시스템에서 이름을 찾은 경우에만 저장한다. */
    private val resolvedCache = ConcurrentHashMap<Int, AppIdentity>()

    /**
     * @param uid 사용량 통계에서 읽은 UID
     * @param storedPackageName 저장 시점에 기록해 둔 패키지명. 앱이 이후 제거되었을 때 이름을 살리는 데 쓴다.
     */
    fun resolve(uid: Int, storedPackageName: String? = null): AppIdentity {
        resolvedCache[uid]?.let { return it }

        specialUidIdentity(uid)?.let { return it }

        val installedIdentity = resolveFromInstalledPackages(uid)
        if (installedIdentity != null) {
            resolvedCache[uid] = installedIdentity
            return installedIdentity
        }

        return resolveFromStoredPackageName(uid, storedPackageName)
    }

    /**
     * 사용량을 저장할 때 함께 기록할 대표 패키지명을 찾는다.
     *
     * @return 찾지 못하면 null. 저장은 그대로 진행하고 표시 이름만 나중에 대체된다.
     */
    fun findPrimaryPackageName(uid: Int): String? =
        packageManager.getPackagesForUid(uid)?.firstOrNull()

    private fun specialUidIdentity(uid: Int): AppIdentity? = when (uid) {
        NetworkStats.Bucket.UID_TETHERING -> AppIdentity(
            uid = uid,
            packageName = null,
            label = context.getString(R.string.app_label_tethering)
        )

        // 커널이 '제거된 UID들'을 한데 모아 주는 버킷이다. 라벨이 이미 그 사실을 말하므로
        // 배지를 덧붙이면 같은 말이 두 번 된다.
        NetworkStats.Bucket.UID_REMOVED -> AppIdentity(
            uid = uid,
            packageName = null,
            label = context.getString(R.string.app_label_removed, uid)
        )

        else -> null
    }

    /**
     * 설치된 패키지 중에서 이름을 찾는다.
     *
     * 하나의 UID를 여러 패키지가 공유할 수 있으므로(`sharedUserId`) 첫 패키지를 대표로 삼는다.
     */
    private fun resolveFromInstalledPackages(uid: Int): AppIdentity? {
        val packageNames = packageManager.getPackagesForUid(uid)
        if (packageNames.isNullOrEmpty()) {
            return null
        }

        val primaryPackageName = packageNames.first()
        val label = loadApplicationLabel(primaryPackageName) ?: primaryPackageName

        return AppIdentity(
            uid = uid,
            packageName = primaryPackageName,
            label = label,
            isInstalled = true
        )
    }

    /**
     * 설치 목록에 없을 때, 저장해 둔 패키지명으로 이름을 복원한다.
     *
     * 여기까지 왔다는 것은 앱이 제거되었거나 조회 권한이 미치지 않는 UID라는 뜻이다.
     * 캐시에 넣지 않는다. 앱이 다시 설치되면 정상 경로로 해결되어야 하기 때문이다.
     *
     * **이 경로로 온 신원은 `isInstalled = false`다.** 지금까지는 저장된 패키지명이
     * 그대로 이름이 되어(`com.example.app`) 사용자가 그것이 지워진 앱인지 알 수 없었다.
     * 이 앱의 존재 이유가 "기록이 앱보다 오래 남는다"이므로, 그 사실을 화면이 말해야 한다.
     */
    private fun resolveFromStoredPackageName(uid: Int, storedPackageName: String?): AppIdentity {
        if (storedPackageName == null) {
            AppLog.warn("Package not found for uid=$uid; 대체 라벨을 사용합니다.")
            return AppIdentity(
                uid = uid,
                packageName = null,
                label = context.getString(R.string.app_label_unknown, uid),
                isInstalled = false
            )
        }

        return AppIdentity(
            uid = uid,
            packageName = storedPackageName,
            label = loadApplicationLabel(storedPackageName) ?: storedPackageName,
            isInstalled = false
        )
    }

    private fun loadApplicationLabel(packageName: String): String? = try {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    } catch (notInstalled: PackageManager.NameNotFoundException) {
        AppLog.debug("Application info missing for $packageName: ${notInstalled.message}")
        null
    }
}
