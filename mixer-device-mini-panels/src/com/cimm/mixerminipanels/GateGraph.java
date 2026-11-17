package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini view for Bitwig's "Gate", drawn like the compressor / Dynamics views (black field, 12 dB grid, dashed 1:1,
 * lilac transfer curve, a faint band at the threshold, white node, no text):
 *  • above the threshold the signal passes 1:1; below it the gate closes and the level drops by Depth;
 *  • one node at the foot of that step: left/right = Threshold, up/down = Depth (down = deeper).
 * Attack / Release are time settings and aren't drawn. Gate's parameters (device knobs Threshold, Depth, Attack,
 * Release; user's screenshot 2026-11-17) are found by name; what was found is logged on first use.
 */
final class GateGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, TRANSFER = CompGraph.TRANSFER, UNITY = CompGraph.UNITY;
    static final iok BAND = DynGraph.BAND;
    static final double LO = -72, HI = 0; // the graph's range in dB
    static final double FULL_DEPTH = 72;  // a "full" depth (or −∞) is drawn as this many dB down

    final Params.P threshold, depth;

    GateGraph(jsd device) {
        super(device, 110);
        threshold = params.findAny("threshold");
        depth = params.findAny("depth", "range", "floor");
        if (MiniGraph.DEBUG) MOD.log("Gate view: threshold=" + DynGraph.id(threshold) + " '" + DynGraph.text(threshold) + "' depth=" + DynGraph.id(depth)
                + " '" + DynGraph.text(depth) + "'");
    }

    /** Is the parameter in dB (its range reaches below 0 or past 1)? Otherwise it's a 0…1 amount. */
    static boolean inDb(Params.P p) { return p != null && (p.min() < 0 || p.max() > 1.0001); }

    double thrDb() {
        if (threshold == null) return -40;
        return inDb(threshold) ? clamp(threshold.value(), LO, HI) : LO + clamp(threshold.norm(), 0, 1) * (HI - LO);
    }
    void setThr(double db, Object undo) {
        db = clamp(db, LO, HI);
        double v = inDb(threshold) ? clamp(db, threshold.min(), threshold.max())
                : threshold.min() + (threshold.max() - threshold.min()) * (db - LO) / (HI - LO);
        set(threshold, v, undo, "Change threshold");
    }

    /** How far the closed gate pulls the level down, in dB (positive). */
    double depthDb() {
        if (depth == null) return FULL_DEPTH;
        if (inDb(depth)) return clamp(Math.abs(depth.value()), 0, FULL_DEPTH);
        return clamp(depth.norm(), 0, 1) * FULL_DEPTH;
    }
    void setDepth(double db, Object undo) {
        db = clamp(db, 0, FULL_DEPTH);
        double v;
        if (inDb(depth)) v = depth.max() <= 0 ? -db : db; // a negative-range depth (−dB) or a positive one
        else v = depth.min() + (depth.max() - depth.min()) * db / FULL_DEPTH;
        set(depth, clamp(v, depth.min(), depth.max()), undo, "Change depth");
    }

    double out(double in) { return in >= thrDb() ? in : in - depthDb(); }

    double px(double db, double w) { return (db - LO) / (HI - LO) * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dbx(double x, double w) { return LO + x / w * (HI - LO); }
    double dby(double y, double h) { return LO + (h - y) / h * (HI - LO); }

    /** The node: at the threshold, at the closed gate's level (clamped into view). */
    double[] node(double w, double h) {
        double t = thrDb();
        return new double[] {px(t, w), clamp(py(t - depthDb(), h), 6, h - 6)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
        double tx = px(thrDb(), w);
        col(g, BAND); g.MRY(tx - 6, 0, 12, h); g.wGc();                  // threshold band

        g.IwV(1);
        col(g, GRIDC);                                                    // faint 12 dB grid
        for (double d = -12; d > LO; d -= 12) {
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        col(g, UNITY); g.IwV(1); CompGraph.dash(g, 3, 3);                 // dashed 1:1
        g.MRY(px(LO, w), py(LO, h)); g.cfM(px(HI, w), py(HI, h)); g.hbP();
        CompGraph.dash(g);

        g.qoU(); g.MRY(0, 0, w, h); g.lYc();
        col(g, TRANSFER); g.IwV(1.6);                                     // transfer: dropped below, 1:1 above
        double t = thrDb(), d = depthDb();
        g.MRY(px(LO, w), py(LO - d, h)); g.cfM(tx, py(t - d, h)); g.cfM(tx, py(t, h)); g.cfM(px(HI, w), py(HI, h));
        g.hbP();
        g.ucQ();

        double[] k = node(w, h);
        col(g, NODE); g.MRY(k[0], k[1], 5); g.wGc();
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] k = node(w, h);
        if (threshold == null || Math.hypot(x - k[0], y - k[1]) > 12) return (ddx, ddy) -> {}; // elsewhere: nothing
        final Object undo = new Object();
        final double[] pos = {k[0], k[1]};
        final double[] d0 = {depthDb()};
        return (ddx, ddy) -> { // left/right: threshold; up/down: depth (down = deeper)
            pos[0] += ddx;
            double thr = clamp(dbx(pos[0], w), LO, HI);
            setThr(thr, undo);
            if (depth != null && ddy != 0) {
                d0[0] = clamp(d0[0] + ddy / h * (HI - LO), 0, FULL_DEPTH);
                setDepth(d0[0], undo);
            }
        };
    }
}
