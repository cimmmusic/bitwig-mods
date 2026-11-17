package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini view for Bitwig's legacy "Compressor" (not Compressor+, whose CompGraph stays unchanged). Drawn exactly like
 * the Compressor+ view so the two read as one family: black field, faint 12 dB grid, dashed 1:1 line, blue threshold line, lilac
 * transfer curve (input left→right, output bottom→top, dB on both axes), a white node at the bend. No text.
 *
 * Legacy Compressor (Bitwig 6.1.4, from the mod log): THRESHOLD dB −60…0, RATIO 0…1 shown "2.00:1",
 * INPUT / OUTPUT dB ±20, MAKEUP_GAIN on/off. It has no knee control (hard knee) and no readable gain-reduction value.
 *  • Ratio is parsed from the parameter's own display text, so the slope matches the device exactly.
 *  • Input moves the bend along the input axis; Output shifts the curve up or down.
 *  • Makeup isn't drawn: its formula isn't published, and we don't guess.
 * The node drags the threshold (with undo), the way the Compressor+ threshold node does. Values are re-read every
 * frame, so automation, the device's knobs and presets show immediately.
 */
final class LegacyCompGraph extends MiniGraph {
    // The Compressor+ view's colours (CompGraph), so both compressors look the same.
    static final iok FIELD = CompGraph.FIELD;
    static final iok GRIDC = CompGraph.GRIDC;
    static final iok LINE = CompGraph.LINE;
    static final iok TRANSFER = CompGraph.TRANSFER;
    static final iok UNITY = CompGraph.UNITY;

    final Params.P thr, ratio, input, output, gr;
    final double LO, HI = 0; // the graph's bottom/left and top/right, dB (as CompGraph: down to the threshold's minimum)

    LegacyCompGraph(jsd device) {
        super(device, 110);
        thr = params.findAny("threshold", "thresh");
        ratio = params.find("ratio");
        input = params.findAny("input", "in_gain");
        output = params.findAny("output", "out_gain");
        gr = params.findAny("gain reduction", "gain_reduction", "reduction");
        LO = thr == null ? -60 : Math.min(-24, thr.min());
        MOD.log("Legacy Compressor view: threshold=" + id(thr) + " ratio=" + id(ratio) + " (text '" + ratioText()
                + "') input=" + id(input) + " output=" + id(output) + " gain reduction=" + id(gr));
    }

    static String id(Params.P p) {
        return p == null ? "none" : p.id() + " [" + p.min() + ", " + p.max() + "] = " + p.value();
    }

    // ---- the device's values ----

    double thrDb() {
        if (thr == null) return -20;
        double v = thr.value();
        if (thr.min() < 0) return clamp(v, LO, HI);            // already dB
        return LO + clamp(thr.norm(), 0, 1) * (HI - LO);       // 0…1 → −60…0
    }

    String ratioText() { return ratio == null ? "" : Params.name(ratio.param()); }

    static final Pattern RATIO = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*:\\s*1");

    double ratioOf() {
        String t = ratioText();
        Matcher m = RATIO.matcher(t == null ? "" : t);
        if (m.find()) {
            try { return Math.max(1, Double.parseDouble(m.group(1).replace(',', '.'))); } catch (NumberFormatException ignored) {}
        }
        if (t != null && t.contains("∞")) return 1000; // ∞:1
        return ratio == null ? 4 : 1 / Math.max(0.001, 1 - clamp(ratio.norm(), 0, 0.999));
    }

    double inDb() { return input == null || input.min() >= 0 ? 0 : input.value(); }
    double outDb() { return output == null || output.min() >= 0 ? 0 : output.value(); }

    /** Output level for an input level: hard knee at the threshold (after the Input gain), then 1/ratio, then Output. */
    double out(double in) {
        double driven = in + inDb(), t = thrDb();
        double comp = driven <= t ? driven : t + (driven - t) / ratioOf();
        return comp + outDb();
    }

    // ---- mapping: linear in dB on both axes, the full view (as CompGraph) ----
    double px(double db, double w) { return (db - LO) / (HI - LO) * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dbx(double x, double w) { return LO + x / w * (HI - LO); }

    /** The node: where the input reaches the threshold (after Input), on the curve. */
    double[] node(double w, double h) {
        double in = clamp(thrDb() - inDb(), LO, HI);
        return new double[] {px(in, w), py(clamp(out(in), LO, HI), h)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
        g.IwV(1);
        col(g, GRIDC);                                                   // faint 12 dB grid
        for (double d = -12; d > LO; d -= 12) {
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        col(g, UNITY); g.IwV(1); CompGraph.dash(g, 3, 3);                // dashed 1:1: the uncompressed path
        g.MRY(px(LO, w), py(LO, h)); g.cfM(px(HI, w), py(HI, h)); g.hbP();
        CompGraph.dash(g);
        double ty = Math.floor(py(clamp(thrDb() + outDb(), LO, HI), h)) + 0.5;
        col(g, LINE); g.IwV(1.4); g.MRY(0, ty); g.cfM(w, ty); g.hbP();  // threshold line
        col(g, TRANSFER); g.IwV(1.6);                                     // transfer curve
        int n = (int) Math.max(16, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double in = LO + (HI - LO) * i / n, x = w * i / n, y = py(clamp(out(in), LO, HI), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
        double[] k = node(w, h);
        col(g, NODE); g.MRY(k[0], k[1], 5); g.wGc();                    // threshold node (same size as every node)
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        // Only describes the drag (Bitwig also asks while hovering); values change in move(). As CompGraph's threshold.
        double[] k = node(w, h);
        if (thr != null && Math.hypot(x - k[0], y - k[1]) <= 12) {
            final Object undo = new Object();
            final double[] pos = {k[0], k[1]};
            return (ddx, ddy) -> {
                pos[0] += ddx; pos[1] += ddy;
                double fromX = dbx(pos[0], w) + inDb(), fromY = LO + (h - pos[1]) / h * (HI - LO) - outDb();
                double db = clamp((fromX + fromY) / 2, LO, HI);
                double v = thr.min() < 0 ? db : thr.min() + (thr.max() - thr.min()) * (db - LO) / (HI - LO);
                set(thr, v, undo, "Change threshold");
            };
        }
        return (ddx, ddy) -> {}; // elsewhere: nothing (and the click doesn't reach the device row)
    }
}
