package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini view for Bitwig's "Transient Control" (knobs: ATTACK Gain / Time, SUSTAIN Gain / Time, detector Peak/RMS, Mix;
 * user's screenshot 2026-11-17): a drum hit's level over time in dB on the compressor views' black field and grid —
 *  • the untouched hit as a faint dashed envelope;
 *  • the shaped hit (user's mockup, 2026-11-17): thick yellow, with a purple edge where the sustain part rules;
 *  • a yellow ATTACK node on the peak and a purple SUSTAIN node on the tail (the device's colours): ATTACK on the transient (up/down = Attack Gain, left/right = Attack Time — how long the transient
 *    part lasts) and SUSTAIN on the tail (up/down = Sustain Gain, left/right = Sustain Time — how far the tail is held).
 * The device's exact envelope maths isn't known: the shape is a guide that follows the knobs, not the audio. IDs (Bitwig
 * 6.1.4 log): ATTACK_GAIN / SUSTAIN −2…+2 (±200 %), ATTACK_TIME / SUSTAIN_TIME log10 s −1.5…−0.5, MIX, ENVELOPE_MODE;
 * gains are drawn as ±12 dB at the knob's ends. Detector and Mix aren't drawn. Parameters are found
 * by name and logged.
 */
final class TransientGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, UNITY = CompGraph.UNITY;
    static final iok ATTACK_C = iok.cfM(240, 205, 70);   // yellow, as the device's attack
    static final iok SUSTAIN_C = iok.cfM(165, 90, 230);  // purple, as the device's sustain
    static final double LO = -42, HI = 10;   // dB shown
    static final double SWING = 12;          // dB at a 0…1 gain knob's ends
    static final double START_T = 0.10, PEAK_T = 0.15; // silence, then the hit rises to its peak (fractions of the width)

    final Params.P aGain, aTime, sGain, sTime;

    TransientGraph(jsd device) {
        super(device, 110);
        aGain = pick("attack", "gain");
        aTime = pick("attack", "time");
        sGain = exact("CONTENTS/SUSTAIN") != null ? exact("CONTENTS/SUSTAIN") : pick("sustain", "gain"); // Bitwig 6.1.4: CONTENTS/SUSTAIN
        sTime = pick("sustain", "time");
        if (MiniGraph.DEBUG) MOD.log("Transient view: attack gain=" + DynGraph.id(aGain) + " time=" + DynGraph.id(aTime)
                + " sustain gain=" + DynGraph.id(sGain) + " time=" + DynGraph.id(sTime));
    }

    private Params.P exact(String id) {
        for (Params.P p : params.all) if (p.id().equalsIgnoreCase(id)) return p;
        return null;
    }

    private Params.P pick(String a, String b) {
        Params.P p = params.find(a + "_" + b);
        return p != null ? p : params.find(a, b);
    }

    // ---- knobs ----
    /** A gain knob drawn as dB: Transient Control's gains are amounts −2…+2 (shown ±200 %, centre = no change), so the
     *  knob's position maps to ±SWING dB at its ends. */
    static double gainDb(Params.P p) {
        if (p == null) return 0;
        return (clamp(p.norm(), 0, 1) * 2 - 1) * SWING;
    }
    static void setGainDb(Params.P p, double db, Object undo, String what) {
        double v = p.min() + (p.max() - p.min()) * (clamp(db / SWING, -1, 1) + 1) / 2;
        set(p, clamp(v, p.min(), p.max()), undo, what);
    }
    static double n(Params.P p, double fallback) { return p == null ? fallback : clamp(p.norm(), 0, 1); }
    static void setN(Params.P p, double v, Object undo, String what) { set(p, p.min() + (p.max() - p.min()) * clamp(v, 0, 1), undo, what); }

    double attackLen() { return 0.03 + n(aTime, 0.4) * 0.30; }  // fraction of the width the transient part lasts
    double sustainLen() { return 0.15 + n(sTime, 0.4) * 0.85; } // how far the tail is held

    // ---- the shape ----
    /** The untouched hit's level at t (0…1 across), dB (user's mockup, 2026-11-17): silence, a steep rise, then a
     *  quick initial drop that settles into a long straight tail (a fast + a slow decay). */
    static double hit(double t) {
        if (t <= START_T) return LO + 2;
        if (t < PEAK_T) return LO + 2 + (-6 - LO - 2) * Math.pow((t - START_T) / (PEAK_T - START_T), 0.7);
        double d = t - PEAK_T;
        return -6 + 20 * Math.log10(0.6 * Math.exp(-d / 0.035) + 0.4 * Math.exp(-d / 0.55));
    }
    /** 1 inside the transient part, falling to 0 after it (Attack Time sets how long); nothing before the hit. */
    double attackWeight(double t) { return t <= START_T ? 0 : t <= PEAK_T ? 1 : Math.exp(-(t - PEAK_T) / attackLen()); }
    /** The sustain part: what the transient part leaves, held for Sustain Time. */
    double sustainWeight(double t) {
        double rest = 1 - attackWeight(t);
        if (t <= START_T) return 0;
        double held = t <= PEAK_T ? 1 : Math.exp(-Math.max(0, t - PEAK_T - attackLen()) / sustainLen());
        return rest * held;
    }
    double shaped(double t) { return hit(t) + gainDb(aGain) * attackWeight(t) + gainDb(sGain) * sustainWeight(t); }

    double px(double t, double w) { return t * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }

    double attackT() { return PEAK_T; }  // on the peak (Attack Time shows in how long the yellow part lasts)
    double sustainT() { return Math.min(0.92, PEAK_T + attackLen() + sustainLen() * 0.45); }
    double[] node(double t, double w, double h) { return new double[] {px(t, w), clamp(py(shaped(t), h), 5, h - 5)}; }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
        g.IwV(1);
        col(g, GRIDC);                                                   // the mockup's grid: 9 across, 5 down
        for (int i = 1; i < 9; i++) { double x = Math.floor(w * i / 9) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        for (int i = 1; i < 5; i++) { double y = Math.floor(h * i / 5) + 0.5; g.MRY(0, y); g.cfM(w, y); g.hbP(); }

        g.qoU(); g.MRY(0, 0, w, h); g.lYc();
        int n = (int) Math.max(40, w / 1.2);
        col(g, UNITY); g.IwV(1.4); CompGraph.dash(g, 3, 3);              // the untouched hit, dashed grey
        for (int i = 0; i <= n; i++) { double t = (double) i / n, y = py(hit(t), h); if (i == 0) g.MRY(0, y); else g.cfM(px(t, w), y); }
        g.hbP();
        CompGraph.dash(g);

        // The shaped hit: thick yellow; where the sustain part rules, a purple edge round a thinner yellow core.
        double[] xs = new double[n + 1], ys = new double[n + 1];
        boolean[] sus = new boolean[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            xs[i] = px(t, w); ys[i] = py(shaped(t), h);
            sus[i] = t > PEAK_T && sustainWeight(t) > attackWeight(t);
        }
        col(g, SUSTAIN_C); g.IwV(3.6);
        for (int i = 1; i <= n; i++) if (sus[i]) { g.MRY(xs[i - 1], ys[i - 1]); g.cfM(xs[i], ys[i]); g.hbP(); }
        col(g, ATTACK_C);
        for (int i = 1; i <= n; i++) { g.IwV(sus[i] ? 1.6 : 3); g.MRY(xs[i - 1], ys[i - 1]); g.cfM(xs[i], ys[i]); g.hbP(); }
        g.ucQ();

        if (aGain != null) { double[] a = node(attackT(), w, h); col(g, ATTACK_C); g.MRY(a[0], a[1], 5); g.wGc(); }   // yellow attack node
        if (sGain != null) { double[] s = node(sustainT(), w, h); col(g, SUSTAIN_C); g.MRY(s[0], s[1], 5); g.wGc(); } // purple sustain node
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] a = node(attackT(), w, h), s = node(sustainT(), w, h);
        double da = Math.hypot(x - a[0], y - a[1]), ds = Math.hypot(x - s[0], y - s[1]);
        final Object undo = new Object();
        final double dbPerPx = (HI - LO) / h;
        if (aGain != null && da <= 12 && da <= ds) {
            final double[] g0 = {gainDb(aGain)}, t0 = {n(aTime, 0.4)};
            return (ddx, ddy) -> { // up/down: Attack Gain; left/right: Attack Time
                if (ddy != 0) { g0[0] -= ddy * dbPerPx; setGainDb(aGain, g0[0], undo, "Change attack gain"); }
                if (ddx != 0 && aTime != null) { t0[0] = clamp(t0[0] + ddx / (w * 0.15), 0, 1); setN(aTime, t0[0], undo, "Change attack time"); }
            };
        }
        if (sGain != null && ds <= 12) {
            final double[] g0 = {gainDb(sGain)}, t0 = {n(sTime, 0.4)};
            return (ddx, ddy) -> { // up/down: Sustain Gain; left/right: Sustain Time
                if (ddy != 0) { g0[0] -= ddy * dbPerPx; setGainDb(sGain, g0[0], undo, "Change sustain gain"); }
                if (ddx != 0 && sTime != null) { t0[0] = clamp(t0[0] + ddx / (w * 0.425), 0, 1); setN(sTime, t0[0], undo, "Change sustain time"); }
            };
        }
        return (ddx, ddy) -> {};
    }
}
