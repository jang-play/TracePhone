package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.update.InstalledAppVersion

class FakeInstalledAppVersion(
    override val versionCode: Int,
    override val versionName: String = "0.0.0"
) : InstalledAppVersion
