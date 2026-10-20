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
import com.dksystems.tracephone.domain.alert.MonthlyLimitNotifier
import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 요금제 한도에 닿았음을 시스템 알림으로 알린다.
 *
 * **알림 ID를 하나만 쓴다.** 앱별 경고와 달리 한도는 기기에 하나뿐이라
 * 80% 알림이 100% 알림으로 대체되는 것이 자연스럽다. 두 개가 나란히 남으면
 * 이미 지난 80% 알림이 지금 상태인 것처럼 보인다.
 *
 * 앱별 경고는 알림 ID로 UID를 쓰므로, 겹치지 않도록 정수 범위 끝을 쓴다.
 */
@Singleton
class SystemMonthlyLimitNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : MonthlyLimitNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun notifyThresholdReached(
        status: MonthlyLimitStatus,
        threshold: MonthlyLimitThreshold
    ) {
        // 권한이 없거나 사용자가 알림을 꺼둔 상태다. 크래시 없이 조용히 넘어간다.
        if (!notificationManager.areNotificationsEnabled()) {
            AppLog.warn(
                "Notifications are disabled; 요금제 한도 알림을 보내지 못했습니다. " +
                    "설정에서 알림을 허용해야 합니다."
            )
            return
        }

        ensureChannelExists()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_usage)
            .setContentTitle(titleFor(threshold))
            .setContentText(
                context.getString(
                    R.string.monthly_limit_alert_message,
                    ByteFormatter.formatCompact(status.usedBytes),
                    ByteFormatter.formatCompact(status.limitBytes),
                    status.remainingDays
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        AppLog.debug("Monthly limit alert notified: threshold=${threshold.percent}%")
    }

    /**
     * 다 쓴 지점만 따로 적고, 나머지는 퍼센트를 넣어 한 문구로 쓴다.
     *
     * 지점이 늘어도 문구를 더하지 않는다. 지점마다 다른 말을 지어내면
     * 같은 사실("얼마나 썼다")을 네 가지 말투로 적게 되고, 번역할 곳도 그만큼 늘어난다.
     */
    private fun titleFor(threshold: MonthlyLimitThreshold): String =
        if (threshold == MonthlyLimitThreshold.REACHED) {
            context.getString(R.string.monthly_limit_alert_title_reached)
        } else {
            context.getString(R.string.monthly_limit_alert_title_warning, threshold.percent)
        }

    private fun ensureChannelExists() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.monthly_limit_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.monthly_limit_channel_description)
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
        const val CHANNEL_ID = "monthly_limit_alerts"

        /** UID(음수 특수값 포함)와 겹치지 않도록 정수 범위 끝을 쓴다. */
        const val NOTIFICATION_ID = Int.MIN_VALUE
        const val OPEN_APP_REQUEST_CODE = 1
    }
}
