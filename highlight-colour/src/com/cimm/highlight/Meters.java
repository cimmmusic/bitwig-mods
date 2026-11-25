package com.cimm.highlight;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * PROBE (temporary): the toolbar's CPU meters (aEJ with the toolbar's aCj/aCk/aCl style) look full all the time on the
 * Minimal theme. For the first two minutes after start, logs each toolbar meter's reading (BDm), peak (fFs), size and
 * orientation on its first paint and again later, to see whether the reading or the drawing is full.
 */
final class Meters {
    private Meters() {}

    private static final long UNTIL = System.currentTimeMillis() + 120_000;
    private static final Map<Object, int[]> seen = new WeakHashMap<>();
    private static Field style;
    private static int logs;

    static void painting(Object meter) {
        if (logs >= 40 || System.currentTimeMillis() > UNTIL || !(meter instanceof bitwig.obf.aEJ m)) return;
        try {
            int[] n;
            synchronized (seen) {
                n = seen.get(m);
                if (n == null) {
                    if (style == null) { style = bitwig.obf.aEJ.class.getDeclaredField("ucQ"); style.setAccessible(true); }
                    Object s = style.get(m);
                    if (s == null || !s.getClass().getName().startsWith("aC")) { seen.put(m, new int[]{-1}); return; }
                    n = new int[]{0}; seen.put(m, n);
                }
            }
            if (n[0] < 0) return;
            int c = n[0]++;
            if (c == 0 || c == 300 || c == 3000) {
                logs++;
                Highlight.MOD.log("PROBE toolbar meter " + style.get(m).getClass().getName() + " paint " + c + ": reading " + m.BDm()
                        + " peak " + m.fFs() + " size " + m.Sxo() + "x" + m.yiX() + " " + m.dEI());
            }
        } catch (Throwable t) { logs = 99; Highlight.MOD.log("PROBE meter failed", t); }
    }
}
