package com.dksystems.tracephone.widget

import android.content.Context
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.UsagePeriod
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 위젯에 쓸 문구를 만든다.
 *
 * 위젯은 Compose의 `stringResource`를 쓸 수 없어 [Context]로 직접 문자열을 읽는다.
 * 그 처리를 한곳에 모아 두어야 화면과 위젯의 표현이 갈라지지 않는다.
 */
internal object WidgetTextFormatter {

    /** 청구 주기 범위. 앱 화면과 같은 형식을 쓴다. */
    fun cycleLabel(context: Context, cycle: UsagePeriod): String {
        val formatter = DateTimeFormatter.ofPattern(
            context.getString(R.string.date_pattern_week_endpoint),
            Locale.getDefault()
        )

        return context.getString(
            R.string.monthly_limit_cycle_range,
            cycle.startDate.format(formatter),
            cycle.endDateInclusive.format(formatter)
        )
    }

    /** 남은 양과 남은 날수. 한도를 넘겼으면 넘긴 양을 알린다. */
    fun remainingLabel(context: Context, status: MonthlyLimitStatus): String =
        if (status.isExceeded) {
            context.getString(
                R.string.monthly_limit_exceeded,
                ByteFormatter.formatCompact(status.usedBytes - status.limitBytes)
            )
        } else {
            context.getString(
                R.string.monthly_limit_remaining,
                ByteFormatter.formatCompact(status.remainingBytes),
                status.remainingDays
            )
        }
}
