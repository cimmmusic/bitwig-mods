package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini view for Bitwig's "Dynamics": drawn like the compressor views (black field, 12 dB grid, dashed 1:1, lilac
 * transfer curve, white nodes, no text), with the device's two thresholds:
 *  • HI: above it the level follows the HI ratio (compression, or expansion upwards);
 *  • LO: below it the level follows the LO ratio (expansion downwards, or upward compression).
 * Between them the signal passes 1:1. A faint band marks each threshold (after the user's mockup, 2026-11-17).
 * Drag a node to move its threshold (with undo).
 *
 * Dynamics (Bitwig 6.1.4, mod log): LOW/HIGH_THRESHOLD dB −60…0, LOW/HIGH_RATIO shown "1:1", LOW/HIGH_KNEE dB 0…24,
 * INPUT_GAIN / OUTPUT_GAIN dB. Parameters are found by name; each ratio is read from the parameter's
 * own display text ("4.00:1", "1:2.00" …), as output-dB per input-dB, so the slopes match the device. What was found
 * is logged on first use.
 */
final class DynGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, TRANSFER = CompGraph.TRANSFER, UNITY = CompGraph.UNITY;
    static final iok BAND = iok.cfM(34, 40, 52); // faint threshold band

    final Params.P loThr, hiThr, loRatio, hiRatio, loKnee, hiKnee, input, output;
    final double LO, HI = 0; // the graph's range in dB

    DynGraph(jsd device) {
        super(device, 110);
        loThr = pick("lo", "threshold", "low", "threshold");
        hiThr = pick("hi", "threshold", "high", "threshold");
        loRatio = pick("lo", "ratio", "low", "ratio");
        hiRatio = pick("hi", "ratio", "high", "ratio");
        loKnee = pick("lo", "knee", "low", "knee");
        hiKnee = pick("hi", "knee", "high", "knee");
        input = params.findAny("input_gain", "input");
        output = params.findAny("output_gain", "output");
        double lo = -60;
        if (loThr != null && loThr.min() < 0) lo = Math.min(lo, loThr.min());
        LO = Math.max(-72, lo);
        if (MiniGraph.DEBUG) MOD.log("Dynamics view: lo thr=" + id(loThr) + " hi thr=" + id(hiThr) + " lo ratio=" + id(loRatio) + " '" + text(loRatio)
                + "' hi ratio=" + id(hiRatio) + " '" + text(hiRatio) + "' knees=" + id(loKnee) + " / " + id(hiKnee) + " input=" + id(input) + " output=" + id(output));
    }

    private Params.P pick(String a1, String b1, String a2, String b2) {
        Params.P p = params.find(a1 + "_" + b1);
        if (p == null) p = params.find(a2 + "_" + b2);
        if (p == null) p = params.find(a1, b1);
        if (p == null) p = params.find(a2, b2);
        return p;
    }

    static String id(Params.P p) { return p == null ? "none" : p.id() + " [" + p.min() + ", " + p.max() + "] = " + p.value(); }
    static String text(Params.P p) { return p == null ? "" : Params.name(p.param()); }

    // ---- the device's values ----
    double db(Params.P p, double fallback) {
        if (p == null) return fallback;
        return p.min() < 0 ? clamp(p.value(), LO, HI) : LO + clamp(p.norm(), 0, 1) * (HI - LO);
    }
    double loDb() { return db(loThr, -40); }
    double hiDb() { return Math.max(loDb(), db(hiThr, -12)); }
    double outDb() { return output == null || output.min() >= 0 ? 0 : output.value(); }
    double inDb() { return input == null || input.min() >= 0 ? 0 : input.value(); }
    /** A knee's width in dB (Dynamics: LOW_KNEE / HIGH_KNEE, 0…24 dB). */
    static double kneeOf(Params.P k) { return k == null ? 0 : Math.abs(k.value()); }

    static final Pattern RATIO = Pattern.compile("([0-9]+(?:[.,][0-9]+)?|∞)\\s*:\\s*([0-9]+(?:[.,][0-9]+)?|∞)");

    /** Output dB per input dB from a ratio's display text: "4:1" → 0.25, "1:2" → 2; 1 if it can't be read. */
    private static final java.util.Map<String, Double> SLOPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Output dB per input dB from a ratio's display text, memoised per text (out() asks for every curve point). */
    static double slope(Params.P p) {
        if (p == null) return 1;
        return SLOPES.computeIfAbsent(text(p), DynGraph::parseSlope);
    }

    static double parseSlope(String text) {
        Matcher m = RATIO.matcher(text);
        if (!m.find()) return 1;
        double a = num(m.group(1)), b = num(m.group(2));
        if (a <= 0) return 1;
        return clamp(b / a, 0, 20);
    }
    static double num(String s) { return s.equals("∞") ? 1e6 : Double.parseDouble(s.replace(',', '.')); }

    /** Output level for an input level: LO slope below the LO threshold, 1:1 between, HI slope above, soft corners. */
    double out(double in) {
        double x = in + inDb(), lo = loDb(), hi = hiDb();
        return corner(x, hi, slope(hiRatio), kneeOf(hiKnee), true) + corner(x, lo, slope(loRatio), kneeOf(loKnee), false) - x + outDb();
    }

    /** 1:1 on one side of threshold t, slope s on the other (above when 'above'), with a quadratic knee of width k. */
    static double corner(double in, double t, double s, double k, boolean above) {
        double d = above ? in - t : t - in; // how far past the threshold, in the direction that changes
        double bent;
        if (k > 0 && Math.abs(d) <= k / 2) { double e = d + k / 2; bent = (s - 1) * e * e / (2 * k); }
        else bent = d <= 0 ? 0 : (s - 1) * d;
        return above ? in + bent : in - bent;
    }

    // ---- mapping (as CompGraph) ----
    double px(double db, double w) { return (db - LO) / (HI - LO) * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dbx(double x, double w) { return LO + x / w * (HI - LO); }

    /** A threshold's node: where the input (after Input gain) reaches it, on the curve. */
    double[] node(double t, double w, double h) { double in = clamp(t - inDb(), LO, HI); return new double[] {px(in, w), py(clamp(out(in), LO, HI), h)}; }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();

        // Threshold bands, behind everything.
        for (double t : new double[] {loDb(), hiDb()}) {
            double x = px(t - inDb(), w);
            col(g, BAND); g.MRY(x - 6, 0, 12, h); g.wGc();
        }

        g.IwV(1);
        col(g, GRIDC);                                                   // faint 12 dB grid
        for (double d = -12; d > LO; d -= 12) {
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        col(g, UNITY); g.IwV(1); CompGraph.dash(g, 3, 3);                // dashed 1:1
        g.MRY(px(LO, w), py(LO, h)); g.cfM(px(HI, w), py(HI, h)); g.hbP();
        CompGraph.dash(g);

        col(g, TRANSFER); g.IwV(1.6);                                     // transfer curve
        int n = (int) Math.max(16, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double in = LO + (HI - LO) * i / n, x = w * i / n, y = py(clamp(out(in), LO, HI), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();

        double[] a = node(loDb(), w, h), b = node(hiDb(), w, h);
        col(g, NODE); g.MRY(a[0], a[1], 5); g.wGc();
        col(g, NODE); g.MRY(b[0], b[1], 5); g.wGc();
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] a = node(loDb(), w, h), b = node(hiDb(), w, h);
        double da = Math.hypot(x - a[0], y - a[1]), dh = Math.hypot(x - b[0], y - b[1]);
        Params.P target = null; double[] start = null;
        if (loThr != null && da <= 12 && da <= dh) { target = loThr; start = a; }
        else if (hiThr != null && dh <= 12) { target = hiThr; start = b; }
        if (target == null) return (ddx, ddy) -> {}; // elsewhere: nothing (and the click doesn't reach the device row)
        final Params.P p = target;
        final Object undo = new Object();
        final double[] pos = {start[0]};
        return (ddx, ddy) -> { // left / right moves that threshold
            pos[0] += ddx;
            double db = clamp(dbx(pos[0], w) + inDb(), LO, HI);
            double v = p.min() < 0 ? db : p.min() + (p.max() - p.min()) * (db - LO) / (HI - LO);
            set(p, v, undo, "Change threshold");
        };
    }
}
