package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini view for Bitwig's "Saturator": the device's own display, kept minimal (no numbers or text; user, 2026-11-17),
 * on the compressor views' black field and 12 dB grid:
 *  • two curves, yellow and orange, as the device: the waveform's two halves, which Skew pulls apart;
 *  • a red Quiet node and a blue Loud node: left/right = threshold, up/down = amount (higher = more %), with small
 *    dots either side showing the knee; a blue line at the loud threshold.
 *
 * Saturator (Bitwig 6.1.4, mod log): LOW_THRESHOLD / HIGH_THRESHOLD dB −40…0, LOW_RATIO −4…1 and HIGH_RATIO −2…0
 * (shown as %), LOW_KNEE dB 0…24, HIGH_KNEE dB −24…0, SKEW_THRESHOLD dB ±12, RATIO_SKEW / KNEE_SKEW ±1, plus DRIVE,
 * NORMALIZE, CUTOFF, POLES, OUTPUT (not part of this display).
 * Slopes as the device shows them: below the quiet threshold 1 − amount (100 % = flat, −148 % = steep), above the loud
 * threshold 1 + amount (−171 % = falling). Skew: the halves' thresholds sit ± half the skew apart, and their amounts
 * and knees are scaled by (1 ± skew). The Skew modelling is approximate; thresholds, amounts and knees are exact.
 */
final class SatGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, UNITY = CompGraph.UNITY;
    static final iok CURVE_A = iok.cfM(240, 205, 70);         // yellow, as the device
    static final iok CURVE_B = iok.cfM(240, 140, 50);         // orange, the other half
    static final iok QUIET = iok.cfM(235, 45, 65);            // red, as the device
    static final iok LOUD = iok.cfM(80, 160, 245);            // blue, as the device
    static final double LO = -48, HI = 0;

    final Params.P loThr, hiThr, loAmt, hiAmt, loKnee, hiKnee, skewThr, skewRatio, skewKnee, drive;
    static final iok DRIVE = FilterGraph.DRIVE; // the same yellow drive line as the Filter+ view

    SatGraph(jsd device) {
        super(device, 110);
        loThr = params.find("contents/low_threshold");
        hiThr = params.find("contents/high_threshold");
        loAmt = params.find("contents/low_ratio");
        hiAmt = params.find("contents/high_ratio");
        loKnee = params.find("contents/low_knee");
        hiKnee = params.find("contents/high_knee");
        skewThr = params.find("contents/skew_threshold");
        skewRatio = params.find("contents/ratio_skew");
        skewKnee = params.find("contents/knee_skew");
        drive = params.find("contents/drive"); // DRIVE 0…3 (linear gain)
    }

    static double v(Params.P p, double fallback) { return p == null ? fallback : p.value(); }
    double loDb() { return clamp(v(loThr, -30), LO, HI); }
    double hiDb() { return clamp(v(hiThr, -10), LO, HI); }

    /** One half of the waveform: sign +1 or −1 picks which way Skew pushes it. */
    double out(double in, int sign) {
        double sk = v(skewThr, 0) / 2 * sign, rs = 1 + v(skewRatio, 0) * sign, ks = Math.max(0, 1 + v(skewKnee, 0) * sign);
        double lo = loDb() + sk, hi = hiDb() + sk;
        double sLo = 1 - v(loAmt, 0) * rs, sHi = 1 + v(hiAmt, 0) * rs;
        double kLo = Math.abs(v(loKnee, 0)) * ks, kHi = Math.abs(v(hiKnee, 0)) * ks;
        return DynGraph.corner(in, hi, sHi, kHi, true) + DynGraph.corner(in, lo, sLo, kLo, false) - in;
    }

    double px(double db, double w) { return (db - LO) / (HI - LO) * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dbx(double x, double w) { return LO + x / w * (HI - LO); }

    /** A node's height from its amount: the parameter's range spread over 10…90 % of the view, higher = more. */
    static double amountY(Params.P p, double h) {
        if (p == null) return h / 2;
        return h * 0.9 - clamp(p.norm(), 0, 1) * h * 0.8;
    }
    static double amountAt(Params.P p, double y, double h) {
        double n = clamp((h * 0.9 - y) / (h * 0.8), 0, 1);
        return p.min() + (p.max() - p.min()) * n;
    }

    double[] quiet(double w, double h) { return new double[] {px(loDb(), w), amountY(loAmt, h)}; }
    double[] loud(double w, double h) { return new double[] {px(hiDb(), w), amountY(hiAmt, h)}; }

    private void curve(com.bitwig.graphics.SYo g, double w, double h, int sign, iok colour) {
        col(g, colour); g.IwV(1.6);
        int n = (int) Math.max(16, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double in = LO + (HI - LO) * i / n, x = w * i / n, y = py(clamp(out(in, sign), LO - 6, HI + 6), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
    }

    private void node(com.bitwig.graphics.SYo g, double[] at, double kneeDb, double w, iok colour) {
        double dx = Math.abs(kneeDb) / (HI - LO) * w / 2; // knee dots: half the knee width either side
        col(g, colour);
        if (dx > 6) { g.MRY(at[0] - dx, at[1], 2); g.wGc(); g.MRY(at[0] + dx, at[1], 2); g.wGc(); }
        g.MRY(at[0], at[1], 5); g.wGc();
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
        g.IwV(1);
        col(g, GRIDC);
        for (double d = -12; d > LO; d -= 12) {
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        double lx = Math.floor(px(hiDb(), w)) + 0.5;                    // loud threshold line
        col(g, LOUD); g.IwV(1); g.MRY(lx, 0); g.cfM(lx, h); g.hbP();
        if (drive != null) { // drive: a yellow line across (height = drive amount) under the curves, its node at the right
            double yd = driveY(h);
            col(g, DRIVE); g.IwV(1.4); g.MRY(0, yd); g.cfM(w, yd); g.hbP();
        }
        g.qoU(); g.MRY(0, 0, w, h); g.lYc();
        curve(g, w, h, -1, CURVE_B);                                    // orange half underneath
        curve(g, w, h, +1, CURVE_A);                                    // yellow half on top
        g.ucQ();
        node(g, quiet(w, h), v(loKnee, 0), w, QUIET);
        node(g, loud(w, h), v(hiKnee, 0), w, LOUD);
        if (drive != null) { col(g, DRIVE); g.MRY(w - 12, driveY(h), 5); g.wGc(); }
    }

    double driveY(double h) { return h - 6 - clamp(drive.norm(), 0, 1) * (h - 12); }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] q = quiet(w, h), l = loud(w, h);
        double dq = Math.hypot(x - q[0], y - q[1]), dl = Math.hypot(x - l[0], y - l[1]);
        final Params.P thr, amt; final double[] start;
        if (loThr != null && dq <= 12 && dq <= dl) { thr = loThr; amt = loAmt; start = q; }
        else if (hiThr != null && dl <= 12) { thr = hiThr; amt = hiAmt; start = l; }
        else if (drive != null && Math.hypot(x - (w - 12), y - driveY(h)) <= 10) { // the drive node: up/down
            final Object undo = new Object();
            final double[] n0 = {clamp(drive.norm(), 0, 1)};
            return (ddx, ddy) -> {
                n0[0] = clamp(n0[0] - ddy / (h - 12), 0, 1);
                set(drive, drive.min() + (drive.max() - drive.min()) * n0[0], undo, "Change drive");
            };
        }
        else return (ddx, ddy) -> {}; // elsewhere: nothing (and the click doesn't reach the device row)
        final Object undo = new Object();
        final double[] pos = {start[0], start[1]};
        return (ddx, ddy) -> { // left/right: threshold; up/down: amount
            pos[0] += ddx; pos[1] += ddy;
            set(thr, clamp(dbx(pos[0], w), thr.min(), thr.max()), undo, "Change threshold");
            if (amt != null) set(amt, amountAt(amt, clamp(pos[1], 0, h), h), undo, "Change amount");
        };
    }
}
