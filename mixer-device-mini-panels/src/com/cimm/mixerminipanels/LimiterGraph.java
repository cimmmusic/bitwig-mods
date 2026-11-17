package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini view for Bitwig's "Peak Limiter", after the user's mockup (2026-11-17): the compressor views' black field and
 * 12 dB grid, a light-blue transfer line that runs 1:1 and bends softly into a flat ceiling, the dashed 1:1 carrying on
 * past the bend (what gets limited), a thin blue line across at the ceiling, and one white node at the bend:
 * up/down = Ceiling, left/right = Input (more input moves the bend left — the signal hits the ceiling sooner).
 * Release is a time and isn't drawn. Parameters (device knobs Input, Release, Ceiling) are found by name and logged.
 */
final class LimiterGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, UNITY = CompGraph.UNITY, LINE = CompGraph.LINE;
    static final iok CURVE = iok.cfM(160, 190, 240); // light blue, as the mockup
    static final double LO = -36, HI = 6;           // the graph's range in dB (both axes)
    static final double KNEE = 3;                    // the soft bend into the ceiling, dB wide

    final Params.P input, ceiling;

    LimiterGraph(jsd device) {
        super(device, 110);
        input = params.findAny("input", "gain");
        ceiling = params.findAny("ceiling", "threshold", "output");
        MOD.log("Peak Limiter view: input=" + DynGraph.id(input) + " ceiling=" + DynGraph.id(ceiling));
    }

    double inDb() { return input == null ? 0 : input.value(); }
    double ceilDb() { return ceiling == null ? 0 : ceiling.value(); }

    /** Output for an input level: input gain, then 1:1 up to the ceiling with a soft bend, flat above. */
    double out(double in) {
        double x = in + inDb(), c = ceilDb();
        return DynGraph.corner(x, c, 0, KNEE, true);
    }

    double px(double db, double w) { return (db - LO) / (HI - LO) * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dbx(double x, double w) { return LO + x / w * (HI - LO); }
    double dby(double y, double h) { return LO + (h - y) / h * (HI - LO); }

    /** The node: where the input (after Input gain) reaches the ceiling. */
    double[] node(double w, double h) {
        double c = ceilDb();
        return new double[] {clamp(px(c - inDb(), w), 4, w - 4), clamp(py(c, h), 4, h - 4)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
        g.IwV(1);
        col(g, GRIDC);                                                  // faint 12 dB grid
        for (double d = -12 * Math.floor(-LO / 12); d < HI; d += 12) {
            if (d <= LO) continue;
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        col(g, UNITY); g.IwV(1); CompGraph.dash(g, 3, 3);               // dashed 1:1 (with Input gain): what's limited
        g.MRY(0, py(LO + inDb(), h)); g.cfM(w, py(HI + inDb(), h)); g.hbP();
        CompGraph.dash(g);

        double cy = Math.floor(py(ceilDb(), h)) + 0.5;
        col(g, LINE); g.IwV(1); g.MRY(0, cy); g.cfM(w, cy); g.hbP();     // the ceiling, across

        g.qoU(); g.MRY(0, 0, w, h); g.lYc();
        col(g, CURVE); g.IwV(2);                                        // transfer line
        int n = (int) Math.max(16, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double in = LO + (HI - LO) * i / n, x = w * i / n, y = py(out(in), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
        g.ucQ();

        double[] k = node(w, h);
        col(g, NODE); g.MRY(k[0], k[1], 5); g.wGc();
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] k = node(w, h);
        if (Math.hypot(x - k[0], y - k[1]) > 12) return (ddx, ddy) -> {}; // elsewhere: nothing
        final Object undo = new Object();
        final double[] pos = {k[0], k[1]};
        return (ddx, ddy) -> { // up/down: ceiling; left/right: input (left = more input)
            pos[0] += ddx; pos[1] += ddy;
            if (ceiling != null && ddy != 0) {
                double c = clamp(dby(pos[1], h), ceiling.min(), ceiling.max());
                set(ceiling, c, undo, "Change ceiling");
            }
            if (input != null && ddx != 0) {
                double gain = clamp(ceilDb() - dbx(pos[0], w), input.min(), input.max());
                set(input, gain, undo, "Change input");
            }
        };
    }
}
