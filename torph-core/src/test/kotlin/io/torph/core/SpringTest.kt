package io.torph.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringTest {
    @Test
    fun `damping ratio maps coefficient`() {
        val p = SpringParams(stiffness = 100f, damping = 20f, mass = 1f)
        assertEquals(1f, p.dampingRatio, 1e-5f)
    }

    @Test
    fun `settle time is finite and shrinks with more damping`() {
        val light = settleTime(100f, 5f, 1f)
        val heavy = settleTime(100f, 15f, 1f)
        assertTrue(light > heavy)
        assertTrue(heavy in 100..10_000)
    }

    @Test
    fun `position settles below precision at settle time`() {
        val p = SpringParams(170f, 26f, 1f)
        val t = settleTime(p)
        assertTrue(kotlin.math.abs(springPosition(p, t)) <= p.precision * 4)
        assertTrue(kotlin.math.abs(springPosition(p, t / 4)) > p.precision)
    }
}
