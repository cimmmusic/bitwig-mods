package com.cimm.highlight;

import bitwig.obf.iok;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background tone and text contrast.
 *
 * Every theme colour passes through Highlight.resolved as Bitwig draws it. Colours are sorted once by name:
 * - background (panels, fields, strokes: any name that isn't text, an icon, a meter, the track default or the accent):
 *   when its value is a neutral grey it gets the user's Background settings (contrast around the theme's dark level,
 *   then luminance, then saturation towards the chosen hue);
 * - foreground (names with text, icon, label...): when the toned background turns light, neutral foregrounds flip
 *   their lightness (white text becomes black), keeping their transparency;
 * - kept as they are: coloured values, "Dark Text" / "Subtle Dark Text" / "Lighter Text" (the text Bitwig puts on
 *   coloured items, chosen per item by contrast, see Highlight.brightness), meters, track colours.
 */
final class Tone {
    private Tone() {}

    static volatile int hue = Highlight.STORE.getInt("bg_hue", 270);
    static volatile int sat = Highlight.STORE.getInt("bg_saturation", 0);
    static volatile int lum = Highlight.STORE.getInt("bg_luminance", 0);
    static volatile int con = Highlight.STORE.getInt("bg_contrast", 0);

    static final byte KEEP = 0, BG = 1, FG = 2;
    private static final Map<Object, Byte> kinds = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> CONTEXT = Set.of("Dark Text", "Subtle Dark Text", "Lighter Text");
    private static final java.util.regex.Pattern FOREGROUND =
            java.util.regex.Pattern.compile("(?i).*(text|icon|label|knob line|hitcount|cursor|playhead|caret).*");
    private static volatile Object windowColour;
    private static volatile iok windowSeen;

    /** Every named theme colour, as Bitwig registers it. */
    static void register(Object colour, String name) {
        byte k;
        if (CONTEXT.contains(name) || name.equals("track") || name.contains("Meter") || Highlight.NAMES.contains(name)) k = KEEP;
        else if (FOREGROUND.matcher(name).matches()) k = FG;
        else k = BG;
        kinds.put(colour, k);
        if (name.equals("Window background")) windowColour = colour;
    }

    static boolean active() { return sat != 0 || lum != 0 || con != 0; }
    private static volatile boolean light;
    private static final ConcurrentHashMap<Long, iok> cache = new ConcurrentHashMap<>();
    private static final iok SAME = new iok(0f, 0f, 0f, 0f);

    /** Settings changed: forget the cached results and work out again whether the background is now light. */
    static void changed() {
        cache.clear();
        iok w = windowSeen;
        light = active() && w != null && luminance(background(w)) > 0.4;
        Highlight.MOD.log("background tone: hue " + hue + "° saturation " + sat + "% luminance " + lum + "% contrast " + con
                + "%" + (light ? " (light: dark text)" : ""));
    }

    /** The colour to draw instead of value for this theme colour. */
    static iok adjust(Object colour, iok value) {
        if (colour == windowColour && windowSeen == null) { windowSeen = value; changed(); }
        if (!active()) return value;
        Byte k = kinds.get(colour);
        if (k == null || k == KEEP) return value;
        long key = ((long) System.identityHashCode(colour) << 32) ^ (pack(value) & 0xFFFFFFFFL);
        iok out = cache.get(key);
        if (out == null) {
            iok r = null;
            if (neutral(value)) r = k == BG ? background(value) : light ? invert(value) : null;
            out = r == null ? SAME : r;
            if (cache.size() < 8192) cache.put(key, out);
        }
        return out == SAME ? value : out;
    }

    // ------------------------------------------------------------------------------------------- the maths

    static boolean neutral(iok c) {
        float max = Math.max(c.FzB(), Math.max(c.IwV(), c.UNj())), min = Math.min(c.FzB(), Math.min(c.IwV(), c.UNj()));
        return max - min < 0.04f || (max > 0 && (max - min) / max < 0.15f);
    }

    /** A background grey toned: contrast around the dark level, luminance, then saturation towards the hue. */
    static iok background(iok c) {
        float[] hsl = hsl(c);
        double l = hsl[2], pivot = 0.1;
        l = pivot + (l - pivot) * (1 + con / 100.0);
        l = Math.max(0, Math.min(1, l));
        double d = lum / 100.0;
        l = d >= 0 ? l + (1 - l) * d : l * (1 + d);
        double s = Math.max(0, Math.min(1, hsl[1] + sat / 100.0));
        return rgb(hue / 360.0, s, Math.max(0, Math.min(1, l)), c.hUR());
    }

    /** A neutral foreground with its lightness flipped (white text on a light background becomes black). */
    static iok invert(iok c) {
        float[] hsl = hsl(c);
        return rgb(hsl[0], hsl[1], 1 - hsl[2], c.hUR());
    }

    /** WCAG relative luminance. */
    static double luminance(iok c) {
        return 0.2126 * lin(c.FzB()) + 0.7152 * lin(c.IwV()) + 0.0722 * lin(c.UNj());
    }
    private static double lin(float v) { return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    private static float[] hsl(iok c) {
        float r = c.FzB(), g = c.IwV(), b = c.UNj();
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
        }
        return new float[]{h, s, l};
    }

    private static iok rgb(double h, double s, double l, float a) {
        if (s <= 0) return new iok((float) l, (float) l, (float) l, a);
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new iok((float) channel(p, q, h + 1 / 3.0), (float) channel(p, q, h), (float) channel(p, q, h - 1 / 3.0), a);
    }
    private static double channel(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1 / 2.0) return q;
        if (t < 2 / 3.0) return p + (q - p) * (2 / 3.0 - t) * 6;
        return p;
    }

    private static int pack(iok c) {
        return (Math.round(c.hUR() * 255) << 24) | (Math.round(c.FzB() * 255) << 16) | (Math.round(c.IwV() * 255) << 8) | Math.round(c.UNj() * 255);
    }

    // ------------------------------------------------------------------------------------------- repainting

    /** Bitwig's windows, found from the widgets that ask for colours (to repaint them all when a setting changes). */
    static final Set<Object> windows = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static int asked;

    static void noteWidget(Object widget) {
        if ((asked++ & 511) != 0 || !(widget instanceof bitwig.obf.dve w)) return;
        try {
            bitwig.obf.dve root = w;
            for (bitwig.obf.dve p = w.RbC(); p != null; p = p.RbC()) root = p;
            windows.add(root);
        } catch (Throwable ignored) {}
    }

    static void repaint() {
        Object[] ws;
        synchronized (windows) { ws = windows.toArray(); }
        for (Object w : ws) try { ((bitwig.obf.dve) w).jbi(); } catch (Throwable ignored) {}
    }
}
