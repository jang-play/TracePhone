package com.dksystems.tracephone.ui.summary

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 좌우로 민 것을 어느 쪽 이동으로 볼지 고정한다.
 *
 * 화살표와 달리 스와이프는 **눌렀는지 아닌지가 화면에 남지 않는다.** 판정이 틀려도
 * 사용자는 '가끔 안 먹는다'고만 느끼고 원인을 짚지 못한다.
 */
class PeriodSwipeTest {

    private val distanceThreshold = 200f
    private val flingVelocity = 500f

    private fun swipe(draggedX: Float, velocityX: Float = 0f) = periodSwipeOf(
        draggedX = draggedX,
        velocityX = velocityX,
        distanceThreshold = distanceThreshold,
        flingVelocity = flingVelocity
    )

    /** 오른쪽으로 미는 것이 과거다. 앱 바의 `<` 화살표와 같은 방향이어야 한다. */
    @Test
    fun `오른쪽으로 충분히 밀면 과거로 간다`() {
        assertEquals(PeriodSwipe.PREVIOUS, swipe(draggedX = 200f))
        assertEquals(PeriodSwipe.PREVIOUS, swipe(draggedX = 400f))
    }

    @Test
    fun `왼쪽으로 충분히 밀면 미래로 간다`() {
        assertEquals(PeriodSwipe.NEXT, swipe(draggedX = -200f))
    }

    @Test
    fun `조금 밀다 만 것은 이동이 아니다`() {
        assertEquals(PeriodSwipe.NONE, swipe(draggedX = 199f))
        assertEquals(PeriodSwipe.NONE, swipe(draggedX = -199f))
        assertEquals(PeriodSwipe.NONE, swipe(draggedX = 0f))
    }

    /**
     * 사람은 끝까지 끌지 않고 툭 튕겨서 넘기려 한다.
     * 거리만 보면 짧고 빠른 동작이 통째로 무시되어 앱이 반응하지 않는 것처럼 느껴진다.
     */
    @Test
    fun `짧게 튕겨도 속도가 빠르면 이동한다`() {
        assertEquals(PeriodSwipe.PREVIOUS, swipe(draggedX = 30f, velocityX = 500f))
        assertEquals(PeriodSwipe.NEXT, swipe(draggedX = -30f, velocityX = -500f))
    }

    @Test
    fun `짧고 느린 동작은 이동이 아니다`() {
        assertEquals(PeriodSwipe.NONE, swipe(draggedX = 30f, velocityX = 499f))
    }

    /**
     * 거리를 먼저 본다. 천천히 충분히 끌었다면 손을 뗄 때 속도가 0이어도 옮겨야 한다.
     * 속도만 보면 '끌어다 놓기'가 통하지 않는다.
     */
    @Test
    fun `충분히 끌었으면 손을 뗄 때 속도가 0이어도 이동한다`() {
        assertEquals(PeriodSwipe.PREVIOUS, swipe(draggedX = 300f, velocityX = 0f))
    }

    /**
     * 끌던 방향과 튕긴 방향이 다를 때는 **끈 거리**를 따른다.
     * 손을 떼며 손가락이 되돌아오는 흔한 동작에서 방향이 뒤집히면 안 된다.
     */
    @Test
    fun `끈 방향과 튕긴 방향이 다르면 끈 방향을 따른다`() {
        assertEquals(PeriodSwipe.PREVIOUS, swipe(draggedX = 300f, velocityX = -800f))
    }
}
