package com.cimm.minimal;

import bitwig.obf.iok;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Readable text on any background, the highlight colour above all: light highlight → black text, dark → white.
 *
 * While Bitwig paints, a stack follows the widget tree (dve.Hnt(g) entry pushes, its return pops): each level holds the
 * colour behind that widget, inherited from its parent and updated when the widget's frame paints its background
 * (eeM's fill, blended by its transparency). When a run of text (or a tinted icon) is drawn, its colour is measured
 * against the colour behind it with WCAG contrast:
 *
 * - Grey/white/black text on a coloured or light background (the highlight, a lit button, a light Look) with less than
 *   4.5:1 becomes black or white, whichever reads better (at least 4.5:1 is always possible with one of them).
 * - Coloured text (the highlight used as a text colour) keeps its hue but is lightened on dark backgrounds, darkened on
 *   light ones, until it reaches 3:1 (WCAG's minimum for large text and interface parts).
 * - Subtle (dimmed) text on ordinary dark surfaces is left alone: the theme dims it on purpose.
 *
 * Results are cached by (text colour, background) so drawing does no colour maths after the first time.
 */
final class Legible {
    private Legible() {}

    static final String KEY = "legible_text";
    static volatile boolean on = Minimal.STORE.getBoolean(KEY, true);

    /** Background stack per painting thread: colour behind each nested widget being painted. */
    private static final class Stack {
        Object[] widget = new Object[64];
        float[] r = new float[64], g = new float[64], b = new float[64];
        int depth; // index of the top; 0 = the window itself
        void grow() {
            int n = widget.length * 2;
            widget = java.util.Arrays.copyOf(widget, n);
            r = java.util.Arrays.copyOf(r, n); g = java.util.Arrays.copyOf(g, n); b = java.util.Arrays.copyOf(b, n);
        }
    }
    private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);

    private static final float[] DEFAULT_BG = {0x11 / 255f, 0x11 / 255f, 0x11 / 255f};

    /** A window starts painting: the stack starts again at its background. */
    static void window() {
        Stack s = STACK.get();
        s.depth = 0;
        s.widget[0] = null;
        iok w = Tone.windowBackground();
        if (w != null) { s.r[0] = w.FzB(); s.g[0] = w.IwV(); s.b[0] = w.UNj(); }
        else { s.r[0] = DEFAULT_BG[0]; s.g[0] = DEFAULT_BG[1]; s.b[0] = DEFAULT_BG[2]; }
    }

    /** A widget starts painting: it sits on its parent's background until it paints its own. */
    static void push(Object widget) {
        Stack s = STACK.get();
        int d = s.depth + 1;
        if (d >= s.widget.length) { if (d > 4096) return; s.grow(); }
        s.widget[d] = widget;
        s.r[d] = s.r[d - 1]; s.g[d] = s.g[d - 1]; s.b[d] = s.b[d - 1];
        s.depth = d;
    }

    /** A widget is done painting. */
    static void pop(Object widget) {
        Stack s = STACK.get();
        if (s.depth <= 0) return;
        if (s.widget[s.depth] == widget) { s.widget[s.depth--] = null; return; }
        // out of step (a paint threw, or a push was skipped): unwind to the widget if it is on the stack
        for (int d = s.depth - 1; d > 0; d--) if (s.widget[d] == widget) { for (int k = d; k <= s.depth; k++) s.widget[k] = null; s.depth = d - 1; return; }
    }

    /** A widget's frame fills its background with c: the colour behind its contents from now on. */
    static void background(Object widget, iok c) {
        if (c == null) return;
        Stack s = STACK.get();
        int d = s.depth;
        if (d <= 0 || s.widget[d] != widget) return;
        float a = c.hUR();
        if (a <= 0.02f) return;
        s.r[d] = c.FzB() * a + s.r[d] * (1 - a);
        s.g[d] = c.IwV() * a + s.g[d] * (1 - a);
        s.b[d] = c.UNj() * a + s.b[d] * (1 - a);
    }

    // ------------------------------------------------------------------------------------------- choosing colours

    private static final ConcurrentHashMap<Long, iok> cache = new ConcurrentHashMap<>();
    private static final iok SAME = new iok(0f, 0f, 0f, 0f);
    private static final iok BLACK = new iok(0x11 / 255f, 0x11 / 255f, 0x11 / 255f, 1f);
    private static final iok WHITE = new iok(1f, 1f, 1f, 1f);
    private static int logs;

    static void clear() { cache.clear(); }

    /** The colour to draw text in, given what is behind it now. */
    static iok text(iok c) { return choose(c, 4.5); }

    /** The colour to draw an icon in (interface parts need 3:1). */
    static iok icon(iok c) { return choose(c, 3.0); }

    private static iok choose(iok c, double need) {
        if (!on || c == null) return c;
        Stack s = STACK.get();
        int d = s.depth;
        float br = s.r[d], bg = s.g[d], bb = s.b[d];
        long key = ((long) pack(c.FzB(), c.IwV(), c.UNj(), c.hUR()) << 32) ^ (pack(br, bg, bb, 1f) & 0xFFFFFFFFL) ^ (need > 4 ? 1L << 63 : 0);
        iok out = cache.get(key);
        if (out == null) {
            iok r = null;
            try { r = decide(c, br, bg, bb, need); } catch (Throwable ignored) {}
            out = r == null ? SAME : r;
            if (cache.size() < 8192) cache.put(key, out);
            if (r != null && logs < 6) {
                logs++;
                Minimal.MOD.log("readable text: " + hex(c) + " on " + hex(new iok(br, bg, bb, 1f)) + " drawn " + hex(r));
            }
        }
        return out == SAME ? c : out;
    }

    /** null: c reads well enough (or is left as the theme meant it). */
    static iok decide(iok c, float br, float bg, float bb, double need) {
        float a = c.hUR();
        if (a < 0.05f) return null;
        double lb = lum(br, bg, bb);
        double lt = lum(c.FzB() * a + br * (1 - a), c.IwV() * a + bg * (1 - a), c.UNj() * a + bb * (1 - a));
        if (ratio(lt, lb) >= need) return null;
        boolean bgColoured = saturation(br, bg, bb) > 0.25f || lb > 0.18;
        if (Tone.neutral(c)) {
            if (!bgColoured) return null; // dimmed text on an ordinary dark surface: on purpose
            boolean black = ratio(lum(BLACK), lb) >= ratio(1.0, lb);
            iok pick = black ? BLACK : WHITE;
            return new iok(pick.FzB(), pick.IwV(), pick.UNj(), Math.max(a, 0.85f));
        }
        // coloured text: same hue, lighter or darker until it reaches 3:1
        double want = Math.min(need, 3.0);
        boolean lighten = lb < 0.18;
        float[] hsl = hsl(c.FzB(), c.IwV(), c.UNj());
        for (int i = 1; i <= 20; i++) {
            float l = lighten ? hsl[2] + (1 - hsl[2]) * i / 20f : hsl[2] * (1 - i / 20f);
            float[] rgb = rgb(hsl[0], hsl[1], l);
            float aa = Math.max(a, 0.9f);
            double lt2 = lum(rgb[0] * aa + br * (1 - aa), rgb[1] * aa + bg * (1 - aa), rgb[2] * aa + bb * (1 - aa));
            if (ratio(lt2, lb) >= want) return new iok(rgb[0], rgb[1], rgb[2], aa);
        }
        return lighten ? WHITE : BLACK;
    }

    /** The highlight colour moved just far enough in lightness to stand 3:1 against the background (same hue). */
    static java.awt.Color readableHighlight(java.awt.Color t) {
        if (!on || t == null) return t;
        iok w = Tone.windowBackground();
        float br = w != null ? w.FzB() : DEFAULT_BG[0], bg = w != null ? w.IwV() : DEFAULT_BG[1], bb = w != null ? w.UNj() : DEFAULT_BG[2];
        iok c = new iok(t.getRed() / 255f, t.getGreen() / 255f, t.getBlue() / 255f, 1f);
        if (ratio(lum(c), lum(br, bg, bb)) >= 3.0) return t;
        iok r = decide(new iok(c.FzB(), c.IwV(), c.UNj(), 1f), br, bg, bb, 3.0);
        if (r == null || Tone.neutral(c)) return t;
        java.awt.Color out = new java.awt.Color(clamp(r.FzB()), clamp(r.IwV()), clamp(r.UNj()));
        Minimal.MOD.log("highlight " + hex(c) + " is hard to see on " + hex(new iok(br, bg, bb, 1f)) + ": drawn " + hex(r));
        return out;
    }

    // ------------------------------------------------------------------------------------------- maths

    static double lum(iok c) { return lum(c.FzB(), c.IwV(), c.UNj()); }
    static double lum(float r, float g, float b) { return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b); }
    private static double lin(float v) { return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    static double ratio(double l1, double l2) { return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05); }

    private static float saturation(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max <= 0 ? 0 : (max - min) / max;
    }

    private static float[] hsl(float r, float g, float b) {
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

    private static float[] rgb(float h, float s, float l) {
        if (s <= 0) return new float[]{l, l, l};
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new float[]{ch(p, q, h + 1 / 3f), ch(p, q, h), ch(p, q, h - 1 / 3f)};
    }
    private static float ch(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6f) return p + (q - p) * 6 * t;
        if (t < 1 / 2f) return q;
        if (t < 2 / 3f) return p + (q - p) * (2 / 3f - t) * 6;
        return p;
    }

    private static int pack(float r, float g, float b, float a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
    private static int clamp(float v) { return Math.round(Math.max(0, Math.min(1, v)) * 255); }
    private static String hex(iok c) { return String.format("#%02X%02X%02X", clamp(c.FzB()), clamp(c.IwV()), clamp(c.UNj())); }
}
