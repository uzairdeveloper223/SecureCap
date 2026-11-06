package com.uzair.securecap.engine;

import java.util.ArrayList;
import java.util.List;

/** Keeps audio and video on one monotonic clock, removing pause intervals. */
final class Timeline {
    final long originUs = System.nanoTime() / 1000;
    private final List<long[]> pauses = new ArrayList<>();
    private boolean paused;
    private long stoppedUs;

    synchronized void pause() {
        if (!paused) { pauses.add(new long[]{System.nanoTime() / 1000, Long.MAX_VALUE}); paused = true; }
    }
    synchronized void resume() {
        if (paused) { pauses.get(pauses.size() - 1)[1] = System.nanoTime() / 1000; paused = false; }
    }
    synchronized void stop() { if (stoppedUs == 0) stoppedUs = System.nanoTime() / 1000; }
    synchronized long map(long absoluteUs) {
        if (absoluteUs < originUs) return -1;
        long removed = 0;
        for (long[] interval : pauses) {
            if (absoluteUs >= interval[0] && absoluteUs < interval[1]) return -1;
            if (absoluteUs >= interval[1]) removed += interval[1] - interval[0];
        }
        return absoluteUs - originUs - removed;
    }
    synchronized long elapsedMs() {
        long now = stoppedUs == 0 ? System.nanoTime() / 1000 : stoppedUs;
        if (paused) now = pauses.get(pauses.size() - 1)[0];
        long removed = 0;
        for (long[] interval : pauses) if (interval[1] != Long.MAX_VALUE) removed += interval[1] - interval[0];
        return Math.max(0, (now - originUs - removed) / 1000);
    }
}
