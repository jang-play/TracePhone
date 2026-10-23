package com.dksystems.tracephone.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppListLayout
import com.dksystems.tracephone.domain.model.AppListScale
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.ui.component.AppIcon
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.component.UsageScaleBar
import com.dksystems.tracephone.ui.theme.UsageSubValueTextStyle
import com.dksystems.tracephone.ui.theme.UsageTheme
import com.dksystems.tracephone.ui.theme.UsageValueTextStyle

/**
 * 목록 한 줄의 아이콘 크기와 위아래 여백.
 *
 * **둘을 더한 48dp가 바닥이다.** 이보다 낮추면 터치 대상이 권장 최소 크기에 못 미쳐,
 * 화면에는 더 들어오지만 정작 누르기 어려운 목록이 된다. 40dp 아이콘을 32dp로 줄인 것도
 * 이 한계에 맞춰 남는 여백을 걷어내기 위해서다.
 */
private val APP_ICON_SIZE = 32.dp
private val ROW_VERTICAL_PADDING = 8.dp

/** 아이콘과 본문 사이 간격. 아이콘에 딸린 줄을 들여쓸 때도 같은 값을 쓴다. */
private val ROW_ICON_GAP = 12.dp

/**
 * 크기를 나타내는 배경 띠의 진하기.
 *
 * 글자 뒤에 깔리므로 읽기를 방해하지 않을 만큼만 옅게 둔다.
 */
private const val SIZE_BAND_ALPHA = 0.07f

/** 줄 안에서 값들을 떼어 놓는 간격. */
private val VALUE_GAP = 8.dp

/**
 * 간단히 보기에서 하루 기준을 가리키는 눈금.
 *
 * 띠보다는 진하되 글자를 가리지 않을 만큼만 둔다. 줄 높이를 통째로 쓰는 선이라
 * 조금만 진해도 눈에 들어온다.
 */
private const val BAND_MARKER_ALPHA = 0.22f
private val BAND_MARKER_WIDTH = 2.dp

/**
 * 목록 한 줄. **큰 숫자는 언제나 모바일 사용량이다.**
 *
 * 이 앱의 목적은 요금이 붙는 모바일을 얼마나 썼는지 보는 것이다.
 * 합계를 크게 적으면 Wi-Fi로 부풀려진 숫자가 먼저 읽혀 판단을 흐린다.
 *
 * 모양은 [AppListLayout]에 따라 둘 중 하나다. 어느 쪽이든 읽히는 값은 같고,
 * 막대를 또렷이 보느냐와 한 화면에 몇 개가 들어오느냐만 다르다.
 *
 * ```
 * DETAILED                                COMPACT
 * [아이콘] 카카오톡        22%  800 MB     [아이콘] 카카오톡  Wi-Fi 440 MB  22% 800 MB
 *          ████████░░░  Wi-Fi 440 MB
 * ```
 *
 * ### 정렬이 Wi-Fi순일 때는 순서와 큰 숫자가 어긋난다
 * 큰 숫자를 정렬 기준값으로 바꾸면 더 나쁜 일이 생긴다 — `MOBILE_BACKGROUND` 정렬에서
 * `sortKeyOf`는 포그라운드 구분이 없는 앱에 0을 돌려주므로, 데이터를 분명히 쓴 앱에
 * 커다랗게 `0 B`가 찍힌다. 목록의 순서는 정렬 칩이 말해 주고,
 * 정렬 기준값은 [secondaryTextOf]가 작은 글자로 함께 적는다.
 */
@Composable
internal fun AppUsageRow(
    usage: AppUsage,
    rowContext: AppUsageRowContext,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val texts = AppUsageRowTexts(
        share = shareTextOf(
            usageBytes = usage.mobile.totalBytes,
            listTotalBytes = rowContext.scale.maxBytes
        ),
        secondary = secondaryTextOf(usage, rowContext.sortOrder)
    )
    val description = rememberAppUsageRowDescription(usage, texts)

    val rowModifier = modifier
        .fillMaxWidth()
        .sizeBand(rowContext, usage)
        .clickable(
            onClickLabel = stringResource(R.string.summary_app_row_action),
            onClick = onClick
        )
        .padding(vertical = ROW_VERTICAL_PADDING)
        // 라벨 없는 숫자들이 따로 읽히면 어느 값이 무엇인지 알 수 없다. 한 문장으로 대신 읽는다.
        // clickable이 이 앞에 있어야 두 번 탭 동작이 살아남는다. 순서가 중요하다.
        .clearAndSetSemantics { contentDescription = description }

    when (rowContext.layout) {
        AppListLayout.DETAILED ->
            DetailedAppUsageRow(usage, rowContext, texts, rowModifier)

        AppListLayout.COMPACT ->
            CompactAppUsageRow(usage, texts, rowModifier)
    }
}

/**
 * 한 줄 안에 적는 보조 글자들.
 *
 * 화면이 적는 글자와 스크린 리더가 읽는 글자가 같아야 하므로 한 번 만들어 양쪽에 건넨다.
 * 따로 만들면 한쪽만 고쳐져 보이는 것과 들리는 것이 어긋난다.
 */
internal data class AppUsageRowTexts(val share: String, val secondary: String)

/**
 * 자세히 보기. 이름 아래에 막대를 진하게 그린다.
 *
 * 막대를 이름과 같은 세로선에서 시작하도록 아이콘 폭만큼 들여쓴다.
 * 왼쪽 끝에 붙이면 아이콘 아래에 걸쳐 어느 줄에 딸린 막대인지 흐려진다.
 */
@Composable
private fun DetailedAppUsageRow(
    usage: AppUsage,
    rowContext: AppUsageRowContext,
    texts: AppUsageRowTexts,
    modifier: Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_ICON_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = usage.app.packageName, size = APP_ICON_SIZE)

            AppNameText(app = usage.app, modifier = Modifier.weight(1f))

            MobileValue(bytes = usage.mobile.totalBytes, shareText = texts.share)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = APP_ICON_SIZE + ROW_ICON_GAP),
            horizontalArrangement = Arrangement.spacedBy(VALUE_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UsageScaleBar(
                fraction = rowContext.mobileFractionOf(usage),
                color = UsageTheme.colors.network.mobile,
                modifier = Modifier.weight(1f),
                markerFraction = rowContext.scale.referenceFraction
            )

            SecondaryValueText(text = texts.secondary)
        }
    }
}

/**
 * 간단히 보기. 한 줄에 모두 넣고 크기는 줄 뒤에 깔린 띠가 말한다.
 *
 * 막대 줄이 없어 한 화면에 두 배로 들어온다. 대신 띠가 옅어 비슷한 두 앱은 구분되지 않는다.
 */
@Composable
private fun CompactAppUsageRow(
    usage: AppUsage,
    texts: AppUsageRowTexts,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(VALUE_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = usage.app.packageName, size = APP_ICON_SIZE)

        AppNameText(
            app = usage.app,
            modifier = Modifier
                .weight(1f)
                .padding(start = ROW_ICON_GAP - VALUE_GAP)
        )

        SecondaryValueText(text = texts.secondary)

        MobileValue(bytes = usage.mobile.totalBytes, shareText = texts.share)
    }
}

/**
 * 크기를 나타내는 배경 띠. **간단히 보기에서만 깐다.**
 *
 * 자세히 보기에는 진한 막대가 따로 있어, 띠까지 깔면 같은 값을 두 번 그리게 된다.
 * 띠가 줄 높이를 통째로 덮으므로 간단히 보기에서는 줄 경계도 함께 드러난다.
 */
@Composable
private fun Modifier.sizeBand(rowContext: AppUsageRowContext, usage: AppUsage): Modifier {
    if (rowContext.layout != AppListLayout.COMPACT) {
        return this
    }

    val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SIZE_BAND_ALPHA)
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = BAND_MARKER_ALPHA)
    val fraction = rowContext.mobileFractionOf(usage)
    val markerFraction = rowContext.scale.referenceFraction

    return drawBehind {
        drawRect(color = bandColor, size = Size(size.width * fraction, size.height))

        // 눈금은 띠 위에도, 빈 자리에도 놓일 수 있다. 띠가 워낙 옅어 한 색으로 양쪽에서 보인다.
        if (markerFraction != null) {
            val markerWidth = BAND_MARKER_WIDTH.toPx()
            val markerLeft = (size.width * markerFraction - markerWidth / 2f)
                .coerceIn(0f, size.width - markerWidth)

            drawRect(
                color = markerColor,
                topLeft = Offset(markerLeft, 0f),
                size = Size(markerWidth, size.height)
            )
        }
    }
}

@Composable
private fun AppNameText(app: AppIdentity, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 지워진 앱은 저장된 패키지명이 그대로 이름이 되어(`com.example.app`)
        // 그것이 무엇인지도, 지워졌다는 사실도 알 수 없었다.
        //
        // 색을 쓰지 않는다. 지워진 앱은 **문제가 아니라 사실**이고,
        // 이 앱에서 색은 문제가 있을 때만 쓴다.
        //
        // 두 레이아웃 모두에 넣는다. 한쪽에만 두면 모양을 바꿨을 뿐인데 정보가 사라진다.
        if (!app.isInstalled) {
            Text(
                text = stringResource(R.string.summary_app_not_installed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 모바일 사용량과 그 몫을 한 줄에 적는다.
 *
 * 몫을 **값 왼쪽**에 둔다. 오른쪽에 두면 `5%`와 `<1%`의 폭이 달라 줄마다 값의 오른쪽 끝이
 * 어긋나고, 고정폭 글꼴로 자릿수를 맞춰 온 이유가 없어진다.
 */
@Composable
private fun MobileValue(bytes: Long, shareText: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = shareText,
            style = UsageSubValueTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ByteText(bytes = bytes, style = UsageValueTextStyle)
    }
}

/** 참고로만 두는 값. 큰 숫자에 밀리도록 작고 옅게 적는다. */
@Composable
private fun SecondaryValueText(text: String) {
    Text(
        text = text,
        style = UsageSubValueTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

/**
 * 목록 전체를 봐야 정할 수 있는 값들.
 *
 * 줄 하나만 보고는 막대를 얼마나 채울지도, 몫이 몇 퍼센트인지도 알 수 없다.
 *
 * @property scale 모든 줄이 함께 쓰는 자. 이것이 같아야 앱끼리 비교된다.
 */
internal data class AppUsageRowContext(
    val sortOrder: UsageSortOrder,
    val layout: AppListLayout,
    val scale: AppListScale
) {

    /** 막대와 배경 띠의 길이. 둘 다 모바일을 재므로 같은 값을 쓴다. */
    fun mobileFractionOf(usage: AppUsage): Float = scale.fractionOf(usage.mobile.totalBytes)
}

/**
 * 몫을 화면에 적을 글자로.
 *
 * 1%가 안 되는 몫을 `0%`로 적으면 '쓰지 않았다'로 읽힌다. 그 둘을 구분해야 목록 아래쪽의
 * 작은 앱들이 왜 거기 있는지 설명된다.
 *
 * **분모는 막대의 자와 같은 값을 받아야 한다.** 한때 막대는 '가장 큰 앱' 대비, 몫은 '전체'
 * 대비였다. 그러면 1등 줄에 `54%`라는 숫자와 가득 찬 막대가 나란히 놓여 한 줄 안에서
 * 두 표시가 어긋난다.
 *
 * 줄 하나에 필요한 값만 받는다 - 레이아웃·정렬까지 들고 있는
 * [AppUsageRowContext]를 요구하지 않아 '모든 앱' 화면도 같은 규칙을 쓸 수 있다.
 *
 * @param listTotalBytes 막대의 자([AppListScale.maxBytes])와 같은 값
 */
@Composable
internal fun shareTextOf(usageBytes: Long, listTotalBytes: Long): String {
    val percent = usageSharePercent(usageBytes, listTotalBytes)

    return if (percent == 0 && usageBytes > 0L) {
        stringResource(R.string.summary_share_percent_minimal)
    } else {
        stringResource(R.string.summary_share_percent, percent)
    }
}

/**
 * 큰 숫자 옆에 참고로 두는 값. **정렬 기준이 보이게 하는 것이 우선이다.**
 *
 * 무엇을 적을지는 [showsBackgroundValue]가 정한다. 평소에는 Wi-Fi를 적는다.
 *
 * 백분율을 적지 않고 바이트만 적는다. 목록에는 '구분 없음' 몫을 보여줄 자리가 없어
 * 어떤 기준으로 계산하든 상세 화면과 숫자가 어긋나고, 사용자는 둘 중 하나를 오류로 읽는다.
 * 바이트 값은 어느 화면에서나 같다.
 */
@Composable
private fun secondaryTextOf(usage: AppUsage, sortOrder: UsageSortOrder): String =
    if (showsBackgroundValue(sortOrder, usage.usage.hasKnownPortion)) {
        stringResource(
            R.string.state_mobile_background,
            ByteFormatter.format(usage.mobile.backgroundBytes)
        )
    } else {
        stringResource(R.string.summary_value_wifi, ByteFormatter.format(usage.wifi.totalBytes))
    }

@Composable
private fun rememberAppUsageRowDescription(
    usage: AppUsage,
    texts: AppUsageRowTexts
): String {
    val labels = AppUsageRowLabels(
        mobile = stringResource(R.string.summary_app_row_mobile),
        share = stringResource(R.string.summary_app_row_share),
        separator = stringResource(R.string.accessibility_separator),
        notInstalled = stringResource(R.string.summary_app_not_installed)
    )

    return remember(usage, texts, labels) {
        describeAppUsageRow(usage, texts, labels)
    }
}
