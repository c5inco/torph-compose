package des.c5inco.torph.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringTest {
    @Test
    fun `damping coefficient maps to ratio`() {
        // torph's `damping` is a coefficient c; Compose wants the ratio c / (2 sqrt(km)).
        assertEquals(1f, SpringParams(stiffness = 100f, damping = 20f, mass = 1f).dampingRatio, 1e-5f)
    }

    @Test
    fun `settle time matches the spring's motion in every damping regime`() {
        val cases = mapOf(
            "underdamped" to SpringParams(170f, 26f, 1f),
            "lightly damped" to SpringParams(100f, 5f, 1f),
            "critically damped" to SpringParams(100f, 20f, 1f),
            "overdamped" to SpringParams(100f, 40f, 1f),
        )
        for ((name, p) in cases) {
            val t = settleTime(p)
            assertTrue(abs(springPosition(p, t)) <= p.precision * 4, "$name: still moving at settleTime $t ms")
            assertTrue(abs(springPosition(p, t / 4)) > p.precision, "$name: settled well before settleTime $t ms")
        }
    }
}
