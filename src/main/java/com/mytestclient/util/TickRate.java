package com.mytestclient.util;

/**
 * Estimates the server TPS from the interval between world time packets (sent every 20 server
 * ticks). 20 TPS = one packet per second.
 */
public final class TickRate {
    private static final int SAMPLES = 10;
    private static final double[] RATES = new double[SAMPLES];
    private static int count, index;
    private static long last = -1;

    private TickRate() {
    }

    public static void onTimeUpdate() {
        long now = System.nanoTime();
        if (last > 0) {
            double seconds = (now - last) / 1_000_000_000.0;
            if (seconds > 0) {
                RATES[index] = Math.min(20.0, 20.0 / seconds);
                index = (index + 1) % SAMPLES;
                count = Math.min(SAMPLES, count + 1);
            }
        }
        last = now;
    }

    /** Average TPS, or -1 if unknown yet. */
    public static double get() {
        if (count == 0) return -1;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += RATES[i];
        return sum / count;
    }

    /** Seconds since the last time packet (a lag indicator when the server freezes). */
    public static double secondsSinceLast() {
        return last < 0 ? 0 : (System.nanoTime() - last) / 1_000_000_000.0;
    }

    public static void reset() {
        count = 0;
        index = 0;
        last = -1;
    }
}
