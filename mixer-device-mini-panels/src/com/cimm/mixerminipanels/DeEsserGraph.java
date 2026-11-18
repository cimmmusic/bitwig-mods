package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.Locale;

/**
 * Mini view for Bitwig's "De-Esser" (user's screenshot, 2026-11-17: Frequency knob, two detection modes — a gentle
 * and a steeper 4-pole curve —, Listen, a gain-reduction meter, Amount), in the EQ views' language: the frequency
 * grid, an orange curve showing the cut above the frequency (steeper in the 4-pole mode), and one white node:
 * left/right = Frequency, up/down = Amount (down = more). Right-click the node: the mode menu (the choice's own options,
 * as the Filter view). The cut drawn is a guide (a full Amount reaches the bottom of the view); the device works dynamically, only on
 * the sibilant moments. Parameters are found by name.
 */
final class DeEsserGraph extends MiniGraph {
    static final iok GRID_MINOR = EqGraph.GRID_MINOR;
    static final double RANGE = 12;               // dB shown above / below 0 (zoomed in: the cut reads clearly)
    static final double MAX_CUT = RANGE;          // a full Amount reaches the bottom of the view
    static final iok CURVE = iok.cfM(240, 140, 60); // the device's orange

    final Params.P freq, amount;
    final String modeId;

    DeEsserGraph(jsd device) {
        super(device, 110);
        freq = params.findAny("freq", "frequency", "cutoff");
        amount = params.findAny("amount", "reduction", "depth", "threshold");
        String m = null;
        for (String w : new String[] {"MODE", "TYPE", "FILTER", "SLOPE", "SHAPE"})
            for (String id : params.choices.keySet()) if (m == null && id.toUpperCase(Locale.ROOT).contains(w) && !id.toUpperCase(Locale.ROOT).contains("LISTEN")) m = id;
        modeId = m;
        if (DEBUG) MOD.log("De-Esser view: freq=" + DynGraph.id(freq) + " amount=" + DynGraph.id(amount) + " mode=" + modeId);
    }

    // ---- values ----
    double hz() {
        if (freq == null) return 5000;
        return freq.max() > 200 ? freq.value() : 440 * Math.pow(2, (freq.value() - 69) / 12); // Hz, or a pitch
    }
    double freqValue(double hz) { return freq.max() > 200 ? hz : 69 + 12 * Math.log(hz / 440) / Math.log(2); }

    /** The cut drawn, dB (positive): the Amount knob's position over the whole view — a full turn reaches the bottom
     *  (user, 2026-11-18: the change was too small to read). A negative-range amount counts down from its top. */
    double amountN() {
        if (amount == null) return 0.5;
        double n = clamp(amount.norm(), 0, 1);
        return amount.max() <= 0 && amount.min() < 0 ? 1 - n : n;
    }
    double cutDb() { return amountN() * MAX_CUT; }
    void setCut(double db, Object undo) {
        double n = clamp(db / MAX_CUT, 0, 1);
        if (amount.max() <= 0 && amount.min() < 0) n = 1 - n;
        set(amount, amount.min() + (amount.max() - amount.min()) * n, undo, "Change amount");
    }

    /** The steeper (4-pole) mode: by the option's text ("4"), else the second option. */
    boolean steep() {
        Object c = modeId == null ? null : params.choices.get(modeId);
        String t = Params.name(c).toLowerCase(Locale.ROOT);
        if (t.contains("4") || t.contains("24") || t.contains("steep") || t.contains("split")) return true;
        if (!t.isEmpty() && !(c instanceof bitwig.obf.zKO)) return false;
        if (c instanceof bitwig.obf.zKO z) {
            try { Object cur = z.vpw(); int i = 0; for (Object o : z.ODA().TMF()) { if (o == cur) return i == 1; i++; } } catch (Throwable ignored) {}
        }
        return false;
    }

    double db(double f, double fc, double cut, boolean steep) {
        if (cut <= 0) return 0;
        if (!steep) return EqGraph.biquadDb(EqGraph.rbj(EqGraph.Kind.HIGH_SHELF, fc, -cut, 0.8), f);
        return 2 * EqGraph.biquadDb(EqGraph.rbj(EqGraph.Kind.HIGH_SHELF, fc, -cut / 2, 1.6), f); // two sharper halves
    }

    // ---- geometry (as the EQ views) ----
    double fx(double f, double w) { return w * Math.log(f / EqGraph.FMIN) / Math.log(EqGraph.FMAX / EqGraph.FMIN); }
    double xf(double x, double w) { return EqGraph.FMIN * Math.pow(EqGraph.FMAX / EqGraph.FMIN, x / w); }
    double dy(double d, double h) { return h / 2 - d / RANGE * (h / 2 - 5); }
    double yd(double y, double h) { return (h / 2 - y) / (h / 2 - 5) * RANGE; }

    /** The node: at the frequency, as low as the full cut. */
    double[] node(double w, double h) {
        double f = clamp(hz(), EqGraph.FMIN, EqGraph.FMAX);
        return new double[] {fx(f, w), dy(-cutDb(), h)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        g.IwV(1);
        col(g, GRID_MINOR);
        for (double dec = 10; dec <= 10000; dec *= 10)
            for (int m = 2; m <= 9; m++) { double f = dec * m; if (f < EqGraph.FMIN || f > EqGraph.FMAX) continue; double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        col(g, GRID_MAJOR);
        for (double f : new double[] {100, 1000, 10000}) { double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        double y0 = Math.floor(h / 2) + 0.5; g.MRY(0, y0); g.cfM(w, y0); g.hbP();

        double fc = clamp(hz(), EqGraph.FMIN, EqGraph.FMAX), cut = cutDb();
        boolean st = steep();
        col(g, CURVE); g.IwV(1.8);
        int n = (int) Math.max(24, w / 1.5);
        for (int i = 0; i <= n; i++) {
            double x = w * i / n, y = dy(clamp(db(xf(x, w), fc, cut, st), -RANGE, RANGE), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
        double[] k = node(w, h);
        col(g, NODE); g.MRY(k[0], k[1], 5); g.wGc();
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] k = node(w, h);
        if (freq == null || Math.hypot(x - k[0], y - k[1]) > 12) return (ddx, ddy) -> {};
        final Object undo = new Object();
        final double[] pos = {k[0], k[1]};
        return (ddx, ddy) -> { // left/right: frequency; up/down: amount (down = more)
            pos[0] += ddx; pos[1] += ddy;
            set(freq, clamp(freqValue(xf(clamp(pos[0], 0, w), w)), freq.min(), freq.max()), undo, "Change frequency");
            if (amount != null && ddy != 0) setCut(clamp(-yd(pos[1], h), 0, RANGE), undo);
        };
    }

    /** Right-click on the node: the mode menu, as EQ+'s node menu builds it. */
    @Override boolean menu(bitwig.obf.dKW popup, double x, double y, double w, double h) {
        double[] k = node(w, h);
        if (Math.hypot(x - k[0], y - k[1]) > 12) return false;
        Object t = modeId == null ? null : params.choices.get(modeId);
        if (!(t instanceof bitwig.obf.zKO z)) { MOD.log("De-Esser: mode isn't a choice: " + (t == null ? null : t.getClass().getName())); return false; }
        popup.MRY(z.fFs());
        Object current = z.vpw();
        for (Object o : z.ODA().TMF()) {
            bitwig.obf.Dew opt = (bitwig.obf.Dew) o;
            if (opt.wGc()) continue;
            bitwig.obf.dKa row = bitwig.obf.dKa.cfM(opt.IwV(), (Runnable) () -> { z.cfM(opt); jbi(); });
            row.eLY(opt == current ? "radio_menu_item_on.svg" : "device/" + opt.hUR());
            String d = opt.lYc();
            if (d != null && !d.isEmpty()) row.lYc(d);
            popup.hUR(row);
        }
        return true;
    }
}
