package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.BuildConfig
import com.dksystems.tracephone.domain.update.InstalledAppVersion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 빌드에 박힌 버전을 그대로 알려 준다.
 *
 * `PackageManager`로 조회하지 않는다. 지금 돌고 있는 코드의 버전을 알고 싶은 것이고,
 * 그 값은 빌드 시점에 이미 정해져 있다.
 */
@Singleton
class BuildConfigAppVersion @Inject constructor() : InstalledAppVersion {
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val versionName: String = BuildConfig.VERSION_NAME
}
