package dev.abros.rivet.core.map;

/** Bounded share of elapsed frame time, in nanoseconds. */
public final class MapWorkBudget {
    private MapWorkBudget() {}

    public static long samplingNanos(long elapsedNanos) {
        // Long bounds select Math.clamp(long, long, long); its (long, int, int)
        // overload returns int and would overflow before division at normal FPS.
        return Math.clamp(elapsedNanos, 1_000_000L, 50_000_000L) * 86_960L / 1_000_000L;
    }
}
