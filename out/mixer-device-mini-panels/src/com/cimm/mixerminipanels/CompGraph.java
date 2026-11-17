package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini Compressor+: Compressor+'s gain-curve display, small: black field, a faint dashed 1:1 line (uncompressed), the
 * blue threshold line, the lilac transfer curve with its soft knee (input left→right, output bottom→top). Two white nodes:
 *  • threshold, at the bend: drag it (any direction) to move the threshold;
 *  • knee, at the end of the knee: drag it right/up to widen the knee, left/down to narrow it.
 * Everything else is set in Compressor+ itself.
 *
 * Compressor+ (Bitwig 6.1.4): THRESHOLD = dB (−48…0), RATIO = 0…1 (ratio 1/(1−v), "1:2.00" at 0.5),
 * SMOOTH = the Knee knob, 0…1.
 */
final class CompGraph extends MiniGraph {
    static final iok FIELD = iok.cfM(12, 12, 14);
    static final iok GRIDC = iok.cfM(30, 30, 34);
    static final iok LINE = iok.cfM(110, 160, 225);
    static final iok TRANSFER = iok.cfM(185, 176, 205);
    static final iok UNITY = iok.cfM(96, 96, 104);     // faint dashed 1:1 reference
    static final double KNEE_MAX = 24; // dB of knee at Knee = 100 %

    final Params.P thr, ratio, knee;
    final double LO; // bottom/left of the graph: the threshold's lowest value

    CompGraph(jsd device) {
        super(device, 110);
        thr = params.find("contents/threshold");
        ratio = params.find("contents/ratio");
        knee = params.find("contents/smooth");
        LO = thr == null ? -48 : Math.min(-24, thr.min());
    }

    double thrDb() { return thr == null ? -12 : clamp(thr.value(), LO, 0); }
    double ratioOf() { return ratio == null ? 2 : 1 / Math.max(0.002, 1 - ratio.norm()); }
    double kneeDb() { return knee == null ? 0 : clamp(knee.norm(), 0, 1) * KNEE_MAX; }

    /** Output level for an input level: soft knee of width k around the threshold, then 1/ratio. */
    double out(double in) {
        double t = thrDb(), r = ratioOf(), k = kneeDb(), d = in - t;
        if (k > 0 && Math.abs(d) <= k / 2) { double e = d + k / 2; return in + (1 / r - 1) * e * e / (2 * k); }
        return d <= 0 ? in : t + d / r;
    }

    double px(double db, double w) { return (db - LO) / -LO * w; }
    double py(double db, double h) { return h - (db - LO) / -LO * h; }
    double dbx(double x, double w) { return LO + x / w * -LO; }

    /** Where the two nodes sit: the bend (threshold) and the end of the knee (at least a few dB along, to stay grabbable). */
    double[] thresholdNode(double w, double h) { double t = thrDb(); return new double[] {px(t, w), py(clamp(out(t), LO, 0), h)}; }
    double[] kneeNode(double w, double h) {
        double in = Math.min(0, thrDb() + Math.max(kneeDb() / 2, 4));
        return new double[] {px(in, w), py(clamp(out(in), LO, 0), h)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        g.Hnt(FIELD); g.MRY(0, 0, w, h); g.wGc();
        g.IwV(1);
        g.Hnt(GRIDC);                                                   // faint 12 dB grid
        for (double d = -12; d > LO; d -= 12) {
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        g.Hnt(UNITY); g.IwV(1); CompGraph.dash(g, 3, 3);                // dashed 1:1: the uncompressed path
        g.MRY(px(LO, w), py(LO, h)); g.cfM(px(0, w), py(0, h)); g.hbP();
        CompGraph.dash(g);
        double ty = Math.floor(py(thrDb(), h)) + 0.5;
        g.Hnt(LINE); g.IwV(1.4); g.MRY(0, ty); g.cfM(w, ty); g.hbP();  // threshold line
        g.Hnt(TRANSFER); g.IwV(1.6);                                     // transfer curve
        int n = (int) Math.max(16, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double in = LO + (-LO) * i / n, x = w * i / n, y = py(clamp(out(in), LO, 0), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
        double[] k = kneeNode(w, h), t = thresholdNode(w, h);
        g.Hnt(NODE); g.MRY(k[0], k[1], 5); g.wGc();                    // knee (same size as every node)
        g.Hnt(NODE); g.MRY(t[0], t[1], 5); g.wGc();                    // threshold
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        // Only describes the drag (Bitwig also asks while hovering); values change in move().
        double[] t = thresholdNode(w, h), k = kneeNode(w, h);
        double dt = Math.hypot(x - t[0], y - t[1]), dk = Math.hypot(x - k[0], y - k[1]);
        final Object undo = new Object();
        if (thr != null && dt <= 12 && dt <= dk) { // threshold: follow the pointer along the diagonal
            final double[] pos = {t[0], t[1]};
            return (ddx, ddy) -> {
                pos[0] += ddx; pos[1] += ddy;
                double fromX = dbx(pos[0], w), fromY = LO + (h - pos[1]) / h * -LO;
                set(thr, clamp((fromX + fromY) / 2, LO, 0), undo, "Change threshold");
            };
        }
        if (knee != null && dk <= 12) { // knee: horizontal distance from the threshold sets its width
            final double[] pos = {k[0]};
            return (ddx, ddy) -> {
                pos[0] += ddx - ddy; // right or up widens, left or down narrows (either direction works)
                double width = clamp(2 * (dbx(pos[0], w) - thrDb()), 0, KNEE_MAX);
                set(knee, knee.min() + (knee.max() - knee.min()) * width / KNEE_MAX, undo, "Change knee");
            };
        }
        return (ddx, ddy) -> {}; // a click anywhere else in the view does nothing (it must not reach the device row: that switches the device off)
    }

    /** Set the dash pattern (on, off, ... in px); no arguments = solid again. */
    static void dash(com.bitwig.graphics.SYo g, double... pattern) {
        try { g.cfM(pattern, 0); } catch (Throwable ignored) {}
    }
}
