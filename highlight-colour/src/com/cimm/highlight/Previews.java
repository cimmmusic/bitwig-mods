package com.cimm.highlight;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Module previews that match the theme.
 *
 * The Grid's module browser shows each module as a picture stored in its .bwmodule file, rendered by Bitwig with its
 * default theme: module background #323232, knob bodies #555555. On a dark theme the real modules are much darker
 * (Minimal: #171717 and #2E2E2E), so the pictures look like a different device. Bitwig turns the picture into its
 * widget in KL3.cfM(bitmap) (dqW.cfM(bitmap)); just before, each picture is recoloured once: every channel goes through
 * a curve that moves the default theme's greys to the theme's, leaves bright parts (text, white knob caps, colours at
 * full strength) as they are, and keeps anti-aliased edges in step with the new background.
 */
final class Previews {
    private Previews() {}

    static final String KEY = "dark_module_previews";
    static volatile boolean on = Highlight.STORE.getBoolean(KEY, true);

    /** Curve points (from -> to), per 8-bit channel: the default theme's greys to Minimal's. */
    private static final int[][] CURVE = {{0, 0}, {0x32, 0x17}, {0x55, 0x2E}, {0x78, 0x70}, {0xFF, 0xFF}};
    private static final int[] LUT = new int[256];
    static {
        for (int v = 0; v < 256; v++) {
            int k = 1;
            while (k < CURVE.length - 1 && v > CURVE[k][0]) k++;
            int x0 = CURVE[k - 1][0], y0 = CURVE[k - 1][1], x1 = CURVE[k][0], y1 = CURVE[k][1];
            LUT[v] = (int) Math.round(y0 + (double) (v - x0) * (y1 - y0) / Math.max(1, x1 - x0));
        }
    }

    /** Pictures already recoloured (Bitwig may hand the same one over again). */
    private static final Set<Object> done = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static int logs;

    /** Recolours the picture in place, once; anything unexpected leaves it as it is. */
    static void recolour(Object bitmap) {
        if (!on || !(bitmap instanceof com.bitwig.graphics.cairo.CairoBitmap b) || !done.add(bitmap)) return;
        try {
            com.bitwig.graphics.cairo.CairoBitmapSurface s = b.wGc();
            com.bitwig.graphics.Jkg format = s.hUR();
            if (format != com.bitwig.graphics.Jkg.cfM && format != com.bitwig.graphics.Jkg.MRY) return; // not 32-bit
            int stride = s.UNj(), w = s.TMF(), h = s.lYc();
            ByteBuffer px = s.IwV().duplicate().order(ByteOrder.nativeOrder());
            if (w <= 0 || h <= 0 || stride < w * 4 || px.capacity() < stride * h) return;
            for (int y = 0; y < h; y++) {
                int row = y * stride;
                for (int x = 0; x < w; x++) {
                    int i = row + x * 4, p = px.getInt(i), a = p >>> 24;
                    if (a == 0) continue;
                    int r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255;
                    if (a < 255) { // premultiplied: map the colour itself, then multiply back
                        r = Math.min(255, r * 255 / a); g = Math.min(255, g * 255 / a); bl = Math.min(255, bl * 255 / a);
                        r = LUT[r] * a / 255; g = LUT[g] * a / 255; bl = LUT[bl] * a / 255;
                    } else { r = LUT[r]; g = LUT[g]; bl = LUT[bl]; }
                    px.putInt(i, (a << 24) | (r << 16) | (g << 8) | bl);
                }
            }
            if (logs < 3) { logs++; Highlight.MOD.log("module preview " + w + "x" + h + " recoloured to the theme (" + format + ")"); }
        } catch (Throwable t) {
            if (logs < 3) { logs++; Highlight.MOD.log("module preview recolour failed", t); }
        }
    }
}
