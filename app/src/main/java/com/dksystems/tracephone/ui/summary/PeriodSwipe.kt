package com.dksystems.tracephone.ui.summary

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 기간을 옮길 만큼 밀었다고 볼 거리.
 *
 * 너무 짧으면 세로로 훑다가 손가락이 비뚤어진 것만으로 기간이 바뀐다.
 * 너무 길면 한 손으로는 닿지 않는다.
 */
private val SWIPE_DISTANCE_THRESHOLD = 72.dp

/**
 * 거리가 모자라도 기간을 옮길 속도(초당).
 *
 * 사람은 끝까지 끌지 않고 **툭 튕겨서** 넘기려 한다. 거리만 보면 짧고 빠른 동작이
 * 통째로 무시되어 앱이 반응하지 않는 것처럼 느껴진다.
 */
private val SWIPE_FLING_VELOCITY = 200.dp

/** 좌우로 민 결과. */
internal enum class PeriodSwipe { PREVIOUS, NEXT, NONE }

/**
 * 민 거리와 속도로 어느 쪽으로 옮길지 정한다.
 *
 * **거리가 먼저다.** 천천히 충분히 끌었다면 손을 뗄 때 속도가 0에 가까워도 옮겨야 한다.
 * 속도는 거리가 모자랄 때만 본다.
 *
 * 오른쪽으로 미는 것(양수)이 과거다. 앱 바의 `<` 화살표와 같은 방향이라
 * 두 가지 조작이 서로 다른 뜻을 갖지 않는다.
 *
 * 갈 수 있는 방향인지는 여기서 보지 않는다. 그건 화면이 아는 일이고,
 * 이 함수는 '무엇을 하려 했는가'만 답한다.
 */
internal fun periodSwipeOf(
    draggedX: Float,
    velocityX: Float,
    distanceThreshold: Float,
    flingVelocity: Float
): PeriodSwipe = when {
    draggedX >= distanceThreshold -> PeriodSwipe.PREVIOUS
    draggedX <= -distanceThreshold -> PeriodSwipe.NEXT
    velocityX >= flingVelocity -> PeriodSwipe.PREVIOUS
    velocityX <= -flingVelocity -> PeriodSwipe.NEXT
    else -> PeriodSwipe.NONE
}

/**
 * 좌우로 밀어 기간을 옮긴다. 화살표와 같은 일을 손가락으로 한다.
 *
 * ### 세로 스크롤을 건드리지 않는 이유
 * `draggable`은 **가로 방향의 터치 슬롭을 넘은 뒤에야** 포인터를 가져간다.
 * 세로로 훑으면 슬롭을 세로로 먼저 넘어 목록이 가져가고, 슬롭 전의 탭은
 * 아무도 소비하지 않아 줄의 `clickable`에 그대로 닿는다.
 *
 * ### 화살표를 그대로 두는 이유
 * 스와이프는 스크린 리더에 보이지 않는다. 화살표가 없으면 그 사용자에게는
 * 기간을 옮길 방법이 사라진다.
 *
 * 갈 수 없는 방향으로 밀면 아무 일도 하지 않는다. 그 방향의 화살표가 이미 흐려져 있어
 * 왜 안 되는지는 화면이 말하고 있다.
 */
@Composable
internal fun Modifier.periodSwipe(navigation: PeriodNavigation): Modifier {
    val density = LocalDensity.current
    val distanceThreshold = with(density) { SWIPE_DISTANCE_THRESHOLD.toPx() }
    val flingVelocity = with(density) { SWIPE_FLING_VELOCITY.toPx() }

    val draggedX = remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta -> draggedX.floatValue += delta }

    return draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = { draggedX.floatValue = 0f },
        onDragStopped = { velocity ->
            val swipe = periodSwipeOf(
                draggedX = draggedX.floatValue,
                velocityX = velocity,
                distanceThreshold = distanceThreshold,
                flingVelocity = flingVelocity
            )

            // 취소로 끝나는 경우에도 여기를 지난다. 남겨 두면 다음 스와이프가 이어서 세어진다.
            draggedX.floatValue = 0f

            when (swipe) {
                PeriodSwipe.PREVIOUS -> if (navigation.canGoPrevious) navigation.onPrevious()
                PeriodSwipe.NEXT -> if (navigation.canGoNext) navigation.onNext()
                PeriodSwipe.NONE -> Unit
            }
        }
    )
}
