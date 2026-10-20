package com.dksystems.tracephone.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dksystems.tracephone.MainActivity
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.alert.UsageAlertNotifier
import com.dksystems.tracephone.domain.model.AppIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한도를 넘긴 앱을 시스템 알림으로 알린다.
 *
 * **알림 ID로 앱의 UID를 쓴다.** 고정 ID를 쓰면 나중에 한도를 넘긴 앱의 알림이
 * 앞 알림을 덮어써서 사용자가 놓치게 된다. UID는 앱마다 다르므로 각각 남는다.
 */
@Singleton
class SystemUsageAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageAlertNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun notifyDailyLimitExceeded(
        app: AppIdentity,
        usedMobileBytes: Long,
        limitBytes: Long
    ) {
        // 권한이 없거나 사용자가 알림을 꺼둔 상태다. 크래시 없이 조용히 넘어간다.
        if (!notificationManager.areNotificationsEnabled()) {
            AppLog.warn(
                "Notifications are disabled; ${app.label} 경고를 보내지 못했습니다. " +
                    "설정에서 알림을 허용해야 합니다."
            )
            return
        }

        ensureChannelExists()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_usage)
            .setContentTitle(context.getString(R.string.alert_title, app.label))
            .setContentText(
                context.getString(
                    R.string.alert_message,
                    ByteFormatter.formatCompact(usedMobileBytes),
                    ByteFormatter.formatCompact(limitBytes)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        notificationManager.notify(app.uid, notification)
        AppLog.debug("Usage alert notified: uid=${app.uid}, label=${app.label}")
    }

    /**
     * 알림 채널을 만든다. 이미 있으면 시스템이 무시하므로 매번 호출해도 안전하다.
     *
     * 채널을 만들어 두면 사용자가 이 종류의 알림만 따로 끌 수 있다.
     */
    private fun ensureChannelExists() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.alert_channel_description)
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val CHANNEL_ID = "usage_alerts"
        const val OPEN_APP_REQUEST_CODE = 0
    }
}
