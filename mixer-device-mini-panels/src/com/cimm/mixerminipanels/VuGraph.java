package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini VU Meter (Cimm's native "VU Meter" device): one needle meter across the channel, following the louder
 * of the device's two meters (L / R, or M / S), in the device's own look — grey scale arc with ticks, orange 0…+6 zone, cream needle, orange peak-hold marker.
 * No text, like every mini view. Display only: clicks do nothing (Ref / Trim are set on the device).
 *
 * The device sends its live needles as NEEDLE_1 / NEEDLE_2 (dB relative to 0 VU, ~30 times a second) and its
 * held readings as READING_1 / READING_2; we smooth the needles at 60 fps so they move like a needle, not in steps.
 */
final class VuGraph extends MiniGraph {
    static final iok FIELD = iok.cfM(34, 34, 36);
    static final iok SCALE = iok.cfM(196, 196, 196);
    static final iok ORANGE = iok.cfM(242, 140, 43);
    static final iok CREAM = iok.cfM(242, 232, 209);
    static final iok PIVOT_DISC = iok.cfM(92, 92, 94);

    static final double HALF = 0.95; // half the arc's sweep in radians, as on the device

    final Params.P needle1, needle2, hold1, hold2;
    double shown = -60; // smoothed needle position in dB

    VuGraph(jsd device) {
        super(device, 110);
        needle1 = params.find("needle_1");
        needle2 = params.find("needle_2");
        hold1 = params.find("reading_1");
        hold2 = params.find("reading_2");
    }

    @Override boolean animating() { return true; }

    /** 60 times a second: ease the drawn needles toward the device's values, and repaint while they move. */
    @Override void tick() {
        double target = Math.max(db(needle1), db(needle2)); // the louder channel
        double next = shown + (target - shown) * 0.45;
        boolean moved = Math.abs(next - shown) > 0.01;
        shown = next;
        if (moved) jbi();
    }

    static double db(Params.P p) {
        if (p == null) return -60;
        double v = p.value();
        return Double.isFinite(v) ? v : -60;
    }

    /** Scale position 0…1 for a VU value in dB: the same piecewise mapping as the device's scale. */
    static double scaleT(double db) {
        if (db <= -10) return (db + 20) * 0.0105;
        if (db <= -7) return 0.105 + (db + 10) * 0.0443;
        if (db <= -5) return 0.238 + (db + 7) * 0.0665;
        if (db <= -3) return 0.371 + (db + 5) * 0.081;
        if (db <= -1) return 0.533 + (db + 3) * 0.0715;
        if (db <= 0) return 0.676 + (db + 1) * 0.105;
        if (db <= 3) return 0.781 + db * 0.035;
        return 0.886 + (db - 3) * 0.038;
    }

    static double angle(double t) { return -HALF + 2 * HALF * t; }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        g.Hnt(FIELD); g.MRY(0, 0, w, h); g.wGc();
        meter(g, 0, w, h, shown, Math.max(db(hold1), db(hold2)));
    }

    /** Scale numbers like Studio One's mini VU: unsigned, grey below 0, orange from 0 up (dB, text). */
    static final double[] NUMBERS = {-20, -7, -3, 0, 6};         // no +3 label (its tick stays): 6 balances the scale
    static final String[] NUMBER_TEXT = {"20", "7", "3", "0", "6"};
    /** Text scale for the numbers: Bitwig ignores SET_FONT_SIZE for this text, so we scale the drawing instead. */
    static final double NUMBER_SCALE = 0.68;

    /** One meter in the rectangle (x0, 0, w, h): the arc is sized so every number stays inside. */
    private void meter(com.bitwig.graphics.SYo g, double x0, double w, double h, double needleDb, double holdDb) {
        double fontH = 11;
        try { fontH = Math.max(6, g.iHb().FzB()); } catch (Throwable ignored) {} // the UI font's height
        fontH *= NUMBER_SCALE;                                                    // … as drawn
        double numW = textW(g, "20");
        double gapR = 6 + 3 + fontH * 0.75;            // from the arc to the numbers' centres: ticks + space + half a line
        double cx = x0 + w / 2, py = h - 9;            // pivot near the bottom, centred
        double sinH = Math.sin(HALF);
        double rW = (w / 2 - 4 - numW / 2) / sinH - gapR; // widest the end numbers allow
        double rH = py - gapR - fontH * 0.6 - 3;           // tallest the top numbers allow
        double r = Math.min(rW, rH);
        if (r < 10) return;
        double rl = r + gapR;

        // Arc: grey up to 0 VU, thicker orange from 0 to +6.
        double zero = scaleT(0);
        arc(g, cx, py, r, 0, zero, SCALE, 1.2);
        arc(g, cx, py, r + 1, zero, 1, ORANGE, 3);

        // Ticks at the device's labelled values.
        double[] marks = {-20, -10, -7, -5, -3, -1, 0, 3, 6};
        for (double m : marks) {
            double a = angle(scaleT(m)), s = Math.sin(a), c = -Math.cos(a);
            boolean hot = m >= 0;
            g.Hnt(hot ? ORANGE : SCALE); g.IwV(hot ? 1.6 : 1.1);
            g.MRY(cx + s * r, py + c * r); g.cfM(cx + s * (r + 6), py + c * (r + 6)); g.hbP();
        }

        // Numbers, centred on their ticks; one that would touch its neighbour is left out.
        double lastRight = -1e9;
        for (int i = 0; i < NUMBERS.length; i++) {
            double a = angle(scaleT(NUMBERS[i]));
            double tx = cx + Math.sin(a) * rl, ty = py - Math.cos(a) * rl;
            // Bitwig measures text a little narrower than it draws it: never trust less than ~0.62 em per character.
            double tw = Math.max(textW(g, NUMBER_TEXT[i]), NUMBER_TEXT[i].length() * fontH * 0.62);
            double left = tx - tw / 2;
            if (left < lastRight + 3) continue;
            small(g, left, ty + fontH * 0.35, NUMBER_TEXT[i], NUMBERS[i] >= 0 ? ORANGE : SCALE);
            lastRight = left + tw;
        }

        // − and + just inside the ends of the arc.
        double aL = angle(0), aR = angle(1);
        double lx = cx + Math.sin(aL) * (r - 9), ly = py - Math.cos(aL) * (r - 9);
        double rx = cx + Math.sin(aR) * (r - 9), ry = py - Math.cos(aR) * (r - 9);
        small(g, lx - textW(g, "-") / 2, ly + fontH * 0.35, "-", SCALE);
        small(g, rx - textW(g, "+") / 2, ry + fontH * 0.35, "+", ORANGE);

        // Peak-hold marker: a short orange stroke just inside the arc, when above the needle.
        double tn = clamp(scaleT(needleDb), -0.025, 1.03);
        double th = clamp(scaleT(holdDb), -0.025, 1.03);
        if (th > tn + 0.004) {
            double a = angle(th), s = Math.sin(a), c = -Math.cos(a);
            g.Hnt(ORANGE); g.IwV(2);
            g.MRY(cx + s * (r - 9), py + c * (r - 9)); g.cfM(cx + s * (r - 2), py + c * (r - 2)); g.hbP();
        }

        // Needle: from the pivot through the scale, exactly radial so it lines up with the ticks and numbers.
        double a = angle(tn), s = Math.sin(a), c = -Math.cos(a);
        g.Hnt(CREAM); g.IwV(1.4);
        g.MRY(cx + s * 4, py + c * 4); g.cfM(cx + s * (r + 4), py + c * (r + 4)); g.hbP();

        // Pivot: grey disc with an orange cap.
        g.Hnt(PIVOT_DISC); g.MRY(cx, py + 2, 6); g.wGc();
        g.Hnt(ORANGE); g.MRY(cx, py + 2, 3); g.wGc();
    }

    /** The text's width as drawn (at NUMBER_SCALE). */
    static double textW(com.bitwig.graphics.SYo g, String s) {
        try { return g.cfM(s).IwV() * NUMBER_SCALE; } catch (Throwable t) { return s.length() * 6 * NUMBER_SCALE; }
    }

    /** Text at (x, baseline y), drawn at NUMBER_SCALE: save, translate (FzB), scale (Hnt), draw, restore. */
    static void small(com.bitwig.graphics.SYo g, double x, double y, String text, iok colour) {
        g.qoU();
        try {
            g.FzB(x, y);                          // TRANSLATE
            g.Hnt(NUMBER_SCALE, NUMBER_SCALE);    // SCALE
            g.cfM(0, 0, text, colour);
        } finally { g.ucQ(); }
    }

    /** An arc around (cx, cy) from scale position t0 to t1, as a polyline. */
    private static void arc(com.bitwig.graphics.SYo g, double cx, double cy, double r, double t0, double t1, iok colour, double width) {
        g.Hnt(colour); g.IwV(width);
        int n = 32;
        for (int i = 0; i <= n; i++) {
            double a = angle(t0 + (t1 - t0) * i / n);
            double x = cx + Math.sin(a) * r, y = cy - Math.cos(a) * r;
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        return (ddx, ddy) -> {}; // display only; swallowing the click keeps it off the device row (which toggles the device)
    }
}
