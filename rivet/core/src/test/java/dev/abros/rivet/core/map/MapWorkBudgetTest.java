package dev.abros.rivet.core.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapWorkBudgetTest {
    @Test void normalFrameRatesKeepTheIntendedShareOfTime() {
        for (int fps : new int[]{20,30,60,120,144,240,1000}) {
            long elapsed = 1_000_000_000L / fps;
            long budget = MapWorkBudget.samplingNanos(elapsed);
            assertTrue(budget > 0, "Positive budget at " + fps + " FPS");
            assertEquals(elapsed * 0.08696, budget, 1.0);
        }
    }

    @Test void stallsAndAdjacentTickFrameCallsRemainBounded() {
        assertEquals(86_960L, MapWorkBudget.samplingNanos(0));
        assertEquals(86_960L, MapWorkBudget.samplingNanos(-1));
        assertEquals(4_348_000L, MapWorkBudget.samplingNanos(Long.MAX_VALUE));
        assertEquals(1_391_360L, MapWorkBudget.samplingNanos(16_000_000));
    }
}
