package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini view for Bitwig's "Over" (user's screenshot, 2026-11-17: a big dB knob, Knee, Tilt, MB Blend, Make-up, Delta,
 * Wet Gain, Mix), drawn like the compressor / limiter views in Over's own colours:
 *  • a yellow transfer line, 1:1 below the bend and flattening above it, the bend softened by Knee and the result
 *    blended with the dry signal by Mix;
 *  • a dark red fill between the dashed 1:1 and the line: what gets taken off;
 *  • one white node at the bend.
 * The big knob is read either as a threshold (the line flattens at its level; node up/down) or, if its name says
 * drive / gain / input, as drive into a 0 dB ceiling (more drive moves the bend left; node left/right). The other
 * direction sets Knee. Tilt, MB Blend, Make-up, Delta and Wet Gain aren't drawn. Over's parameter list is logged once
 * ("Over parameters:") so the guesses can be checked.
 */
final class OverGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, UNITY = CompGraph.UNITY;
    static final iok CURVE = iok.cfM(240, 205, 70);   // Over's yellow
    static final iok OVER_FILL = iok.cfM(90, 22, 34);  // Over's dark red
    static final double LO = -36, HI = 6;              // dB, both axes
    static final double MAX_KNEE = 24;                 // dB, for a 0…1 knee knob

    final Params.P amount, knee, mix;
    final boolean drive; // the big knob pushes into a 0 dB ceiling (else it is the threshold)
    private static boolean logged;

    OverGraph(jsd device) {
        super(device, 110);
        Params.P a = null;
        for (String w : new String[] {"threshold", "ceiling", "amount", "drive", "level", "gain", "input"}) {
            for (Params.P p : params.all) {
                String k = p.key();
                if (k.contains(w) && !k.contains("knee") && !k.contains("tilt") && !k.contains("blend") && !k.contains("mix") && !k.contains("wet")) { a = p; break; }
            }
            if (a != null) break;
        }
        amount = a;
        knee = params.findAny("knee");
        mix = params.findAny("mix");
        String k = amount == null ? "" : amount.key();
        drive = k.contains("drive") || k.contains("gain") || k.contains("input");
        if (!logged) { // once: Over's ids aren't known yet
            logged = true;
            StringBuilder sb = new StringBuilder("Over parameters:");
            for (Params.P p : params.all) sb.append("\n  ").append(p.id()).append(" '").append(Params.name(p.param())).append("' [").append(p.min()).append(", ").append(p.max()).append("] = ").append(p.value());
            for (String id : params.choices.keySet()) sb.append("\n  (choice) ").append(id).append(" '").append(params.text(id)).append("'");
            sb.append("\n  -> big knob ").append(amount == null ? "none" : amount.id()).append(drive ? " (drive)" : " (threshold)").append(", knee ").append(knee == null ? "none" : knee.id()).append(", mix ").append(mix == null ? "none" : mix.id());
            MOD.log(sb.toString());
        }
    }

    /** The big knob in dB (a 0…1 knob is read as −24…0 dB threshold / 0…24 dB drive). */
    double amountDb() {
        if (amount == null) return drive ? 0 : -6;
        boolean dB = amount.min() < 0 || amount.max() > 1.0001;
        if (dB) return amount.value();
        return drive ? amount.norm() * 24 : -24 + amount.norm() * 24;
    }
    void setAmountDb(double db, Object undo) {
        boolean dB = amount.min() < 0 || amount.max() > 1.0001;
        double v = dB ? db : drive ? db / 24 : (db + 24) / 24;
        set(amount, clamp(v, amount.min(), amount.max()), undo, "Change amount");
    }
    double kneeDb() {
        if (knee == null) return 3;
        return knee.max() > 1.0001 ? clamp(knee.value(), 0, 48) : clamp(knee.norm(), 0, 1) * MAX_KNEE;
    }
    void setKneeDb(double db, Object undo) {
        double v = knee.max() > 1.0001 ? db : db / MAX_KNEE;
        set(knee, clamp(v, knee.min(), knee.max()), undo, "Change knee");
    }
    double mixN() { return mix == null ? 1 : clamp(mix.norm(), 0, 1); }

    double ceil() { return drive ? 0 : amountDb(); }
    double push() { return drive ? amountDb() : 0; }

    /** Output for an input level: (input + drive) into the ceiling with a soft knee, blended with the dry signal by Mix. */
    double out(double in) {
        double wet = DynGraph.corner(in + push(), ceil(), 0, kneeDb(), true);
        double m = mixN();
        if (m >= 0.999) return wet;
        double a = Math.pow(10, wet / 20) * m + Math.pow(10, in / 20) * (1 - m); // blend amplitudes
        return 20 * Math.log10(Math.max(a, 1e-9));
    }

    double px(double db, double w) { return (db - LO) / (HI - LO) * w; }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dbx(double x, double w) { return LO + x / w * (HI - LO); }
    double dby(double y, double h) { return LO + (h - y) / h * (HI - LO); }

    double[] node(double w, double h) {
        double in = ceil() - push();
        return new double[] {clamp(px(in, w), 4, w - 4), clamp(py(out(in), h), 4, h - 4)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
        int n = (int) Math.max(16, w / 1.5);
        g.qoU(); g.MRY(0, 0, w, h); g.lYc();
        // red: between the 1:1 (with drive) and the line, where the line is below it
        col(g, OVER_FILL);
        for (int i = 0; i < n; i++) {
            double in = LO + (HI - LO) * i / n, x0 = w * i / n, x1 = w * (i + 1) / n;
            double top = py(in + push(), h), bot = py(out(in), h);
            if (bot > top + 0.5) { g.MRY(x0, top, x1 - x0 + 0.5, bot - top); g.wGc(); }
        }
        g.ucQ();

        g.IwV(1);
        col(g, GRIDC);                                                   // faint 12 dB grid
        for (double d = -12; d > LO; d -= 12) {
            double x = Math.floor(px(d, w)) + 0.5, y = Math.floor(py(d, h)) + 0.5;
            g.MRY(x, 0); g.cfM(x, h); g.hbP(); g.MRY(0, y); g.cfM(w, y); g.hbP();
        }
        col(g, UNITY); g.IwV(1); CompGraph.dash(g, 3, 3);                // dashed 1:1 (with drive)
        g.MRY(0, py(LO + push(), h)); g.cfM(w, py(HI + push(), h)); g.hbP();
        CompGraph.dash(g);

        g.qoU(); g.MRY(0, 0, w, h); g.lYc();
        col(g, CURVE); g.IwV(2);                                         // the yellow line
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
        if (amount == null || Math.hypot(x - k[0], y - k[1]) > 12) return (ddx, ddy) -> {};
        final Object undo = new Object();
        final double[] pos = {k[0], k[1]};
        final double[] kn = {kneeDb()};
        return (ddx, ddy) -> {
            pos[0] += ddx; pos[1] += ddy;
            if (drive) { // left/right: drive (left = more); up/down: knee (up = softer)
                if (ddx != 0) setAmountDb(clamp(-dbx(pos[0], w), 0, 48), undo);
                if (ddy != 0 && knee != null) { kn[0] = clamp(kn[0] - ddy * 0.25, 0, 48); setKneeDb(kn[0], undo); }
            } else {     // up/down: threshold; left/right: knee (right = softer)
                if (ddy != 0) setAmountDb(clamp(dby(pos[1], h), LO, HI), undo);
                if (ddx != 0 && knee != null) { kn[0] = clamp(kn[0] + ddx * 0.25, 0, 48); setKneeDb(kn[0], undo); }
            }
        };
    }
}
