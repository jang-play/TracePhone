package com.dksystems.tracephone.ui.component

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dksystems.tracephone.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** 아이콘을 그릴 픽셀 크기. 목록에서 쓰는 크기에 맞춰 한 번만 디코딩한다. */
private const val ICON_SIZE_PX = 96

/**
 * 이미 디코딩한 아이콘을 재사용한다.
 *
 * 목록을 스크롤할 때마다 `PackageManager`에서 드로어블을 새로 만들면 프레임이 눈에 띄게 밀린다.
 * 아이콘 수는 설치된 앱 수만큼으로 제한되므로 그대로 들고 있어도 메모리 부담이 크지 않다.
 */
private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

/**
 * 앱 아이콘. 패키지명이 없거나 아이콘을 찾지 못하면 기본 아이콘을 보여준다.
 *
 * @param packageName 대상 앱의 패키지명. 제거된 앱이면 null일 수 있다.
 */
@Composable
fun AppIcon(
    packageName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current

    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = packageName?.let { name ->
            iconCache[name] ?: withContext(Dispatchers.IO) {
                loadIconBitmap(context, name)?.also { iconCache[name] = it }
            }
        }
    }

    val currentIcon = iconBitmap
    if (currentIcon != null) {
        Image(
            bitmap = currentIcon,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size)
        )
    }
}

private fun loadIconBitmap(context: Context, packageName: String): ImageBitmap? = try {
    context.packageManager
        .getApplicationIcon(packageName)
        .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
        .asImageBitmap()
} catch (notInstalled: PackageManager.NameNotFoundException) {
    AppLog.debug("Icon unavailable for $packageName: ${notInstalled.message}")
    null
}
