package com.mytestclient.render;

/** ARGB colour helpers. */
public final class Colors {
    private Colors() {
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    /** Multiplies the alpha channel by {@code f} (0..1). */
    public static int fade(int color, float f) {
        int a = (color >>> 24) & 0xFF;
        return withAlpha(color, Math.round(a * Math.max(0f, Math.min(1f, f))));
    }

    /** Lighter (t > 0) or darker (t < 0) version of a colour. */
    public static int shade(int color, float t) {
        return t >= 0 ? lerp(color, (color & 0xFF000000) | 0xFFFFFF, t) : lerp(color, color & 0xFF000000, -t);
    }

    /** Health colour: green at full, red at empty. */
    public static int health(float fraction) {
        return lerp(0xFFEF4444, 0xFF4ADE80, fraction);
    }
}
