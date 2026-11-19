package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import java.util.Locale;

/**
 * Mini view for Bitwig's plain "Filter" (user's screenshot, 2026-11-17: eight types — low-pass, low-pass 4-pole,
 * band-pass, band-pass 4-pole, high-pass, high-pass 4-pole, notch, off — plus Cutoff, Resonance, Pre / Post), drawn
 * like the Filter+ view: the EQ frequency grid, the response curve in the device's orange, and one white node at the
 * cutoff: left/right = Cutoff, up/down = Resonance. Right-click the node: the filter-type menu, as EQ+'s node menu
 * (the choice's own options, icons and descriptions; the current one marked). Pre / Post gains aren't drawn.
 * Parameters are found by name and logged (DEBUG).
 */
final class PlainFilterGraph extends MiniGraph {
    static final iok GRID_MINOR = EqGraph.GRID_MINOR;
    static final double RANGE = 24; // dB shown above / below 0
    static final iok CURVE = iok.cfM(240, 140, 60); // the device's orange

    final Params.P cutoff, resonance;
    final String typeId;

    PlainFilterGraph(jsd device) {
        super(device, 110);
        cutoff = params.findAny("cutoff", "freq");
        resonance = params.findAny("resonance", "reso");
        typeId = firstChoice("TYPE", "MODE", "FILTER", "SHAPE", "RESPONSE");
        if (DEBUG) MOD.log("Filter view: cutoff=" + DynGraph.id(cutoff) + " resonance=" + DynGraph.id(resonance) + " type=" + typeId
                + " '" + (typeId == null ? "" : params.text(typeId)) + "'");
    }

    String firstChoice(String... words) {
        for (String w : words) for (String id : params.choices.keySet()) if (id.toUpperCase(Locale.ROOT).contains(w)) return id;
        return params.choices.isEmpty() ? null : params.choices.keySet().iterator().next();
    }

    // ---- the filter type ----
    private String typeKey;
    private EqGraph.Kind kindCache = EqGraph.Kind.LOW_PASS;
    private int polesCache = 2;

    /** Kind and poles from the selected option's text, else its position in the list (the screenshot's order). */
    private void refreshType() {
        Object c = typeId == null ? null : params.choices.get(typeId);
        String t = Params.name(c).toLowerCase(Locale.ROOT);
        int index = -1;
        if (c instanceof bitwig.obf.zKO z) {
            try {
                Object cur = z.vpw(); int i = 0;
                for (Object o : z.ODA().TMF()) { if (o == cur) { index = i; break; } i++; }
            } catch (Throwable ignored) {}
        }
        String key = t + "#" + index;
        if (key.equals(typeKey)) return;
        typeKey = key;
        EqGraph.Kind k = null;
        if (t.contains("off") || t.contains("bypass") || t.contains("none")) k = EqGraph.Kind.OFF;
        else if (t.contains("notch") || t.contains("reject")) k = EqGraph.Kind.NOTCH;
        else if (t.contains("band") || t.contains("bp")) k = EqGraph.Kind.BAND_PASS;
        else if (t.contains("high") || t.contains("hp")) k = EqGraph.Kind.HIGH_PASS;
        else if (t.contains("low") || t.contains("lp")) k = EqGraph.Kind.LOW_PASS;
        int poles = t.contains("4") || t.contains("24") ? 4 : 2;
        if (k == null && index >= 0) { // the device's order: LP, LP4, BP, BP4, HP, HP4, notch, off
            EqGraph.Kind[] order = {EqGraph.Kind.LOW_PASS, EqGraph.Kind.LOW_PASS, EqGraph.Kind.BAND_PASS, EqGraph.Kind.BAND_PASS,
                    EqGraph.Kind.HIGH_PASS, EqGraph.Kind.HIGH_PASS, EqGraph.Kind.NOTCH, EqGraph.Kind.OFF};
            k = index < order.length ? order[index] : EqGraph.Kind.LOW_PASS;
            poles = index < 6 && index % 2 == 1 ? 4 : 2;
        }
        kindCache = k == null ? EqGraph.Kind.LOW_PASS : k;
        polesCache = poles;
    }

    // ---- values ----
    double hz() {
        if (cutoff == null) return 1000;
        return cutoff.max() > 200 ? cutoff.value() : 440 * Math.pow(2, (cutoff.value() - 69) / 12);
    }
    double cutoffValue(double hz) { return cutoff.max() > 200 ? hz : 69 + 12 * Math.log(hz / 440) / Math.log(2); }
    double res() { return resonance == null ? 0.2 : clamp(resonance.norm(), 0, 1); }

    /** Response in dB at f: Butterworth sections (2 or 4 poles), the last one's Q raised by the resonance. */
    double db(double f, EqGraph.Kind k, int n, double fc, double r) {
        if (k == EqGraph.Kind.OFF) return 0;
        if (k == EqGraph.Kind.BAND_PASS || k == EqGraph.Kind.NOTCH) {
            double q = 0.5 + r * 8, sum = 0;
            for (int i = 0; i < n / 2; i++) sum += EqGraph.biquadDb(EqGraph.rbj(k, fc, 0, q), f);
            return sum;
        }
        double sum = 0, qRes = 0.707 + r * 9;
        for (int i = 0; i < n / 2; i++) {
            double qk = 1 / (2 * Math.sin((2 * i + 1) * Math.PI / (2 * n)));
            if (i == n / 2 - 1) qk = Math.max(qk, qRes * qk / 0.707);
            sum += EqGraph.biquadDb(EqGraph.rbj(k, fc, 0, qk), f);
        }
        return sum;
    }

    // ---- geometry (as the EQ views) ----
    double fx(double f, double w) { return w * Math.log(f / EqGraph.FMIN) / Math.log(EqGraph.FMAX / EqGraph.FMIN); }
    double xf(double x, double w) { return EqGraph.FMIN * Math.pow(EqGraph.FMAX / EqGraph.FMIN, x / w); }
    double dy(double d, double h) { return h / 2 - d / RANGE * (h / 2 - 5); }

    double[] node(double w, double h) {
        refreshType();
        double f = clamp(hz(), EqGraph.FMIN, EqGraph.FMAX);
        return new double[] {fx(f, w), dy(clamp(db(f, kindCache, polesCache, f, res()), -RANGE, RANGE), h)};
    }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        refreshType();
        g.IwV(1);
        col(g, GRID_MINOR);
        for (double dec = 10; dec <= 10000; dec *= 10)
            for (int m = 2; m <= 9; m++) { double f = dec * m; if (f < EqGraph.FMIN || f > EqGraph.FMAX) continue; double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        col(g, GRID_MAJOR);
        for (double f : new double[] {100, 1000, 10000}) { double x = Math.floor(fx(f, w)) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        double y0 = Math.floor(h / 2) + 0.5; g.MRY(0, y0); g.cfM(w, y0); g.hbP();

        EqGraph.Kind k = kindCache; int n = polesCache;
        double fc = hz(), r = res();
        col(g, CURVE); g.IwV(1.8);                                       // response curve
        int pts = (int) Math.max(24, w / 1.5);
        for (int i = 0; i <= pts; i++) {
            double x = w * i / pts, y = dy(clamp(db(xf(x, w), k, n, fc, r), -RANGE - 6, RANGE), h);
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
        double[] nd = node(w, h);
        col(g, NODE); g.MRY(nd[0], nd[1], 5); g.wGc();
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double[] k = node(w, h);
        if (cutoff == null || Math.hypot(x - k[0], y - k[1]) > 12) return (ddx, ddy) -> {};
        final Object undo = new Object();
        final double[] pos = {k[0]};
        final double[] r0 = {res()};
        final double[] py = {k[1]};
        final boolean[] held = {Math.abs(k[1] - h / 2) < 1};
        return (ddx, ddy) -> { // left/right: cutoff; up/down: resonance (the 0 dB line is sticky)
            pos[0] += ddx;
            set(cutoff, clamp(cutoffValue(xf(clamp(pos[0], 0, w), w)), cutoff.min(), cutoff.max()), undo, "Change cutoff");
            if (resonance != null && ddy != 0) {
                py[0] += ddy;
                r0[0] = clamp(r0[0] - ddy / (h * 0.8), 0, 1);
                if (sticky(held, py[0] - h / 2)) {
                    double fc = clamp(hz(), EqGraph.FMIN, EqGraph.FMAX);
                    EqGraph.Kind kd = kindCache; int n = polesCache;
                    double rz = resonanceForZero(rs -> db(fc, kd, n, fc, rs));
                    if (!Double.isNaN(rz)) r0[0] = rz; // the resonance that puts the cutoff on 0 dB
                }
                set(resonance, resonance.min() + (resonance.max() - resonance.min()) * r0[0], undo, "Change resonance");
            }
        };
    }

    /** Right-click on the node: the filter-type menu, as EQ+'s node menu builds it (aSs). */
    @Override boolean menu(bitwig.obf.dKW popup, double x, double y, double w, double h) {
        double[] k = node(w, h);
        if (Math.hypot(x - k[0], y - k[1]) > 12) return false;
        Object t = typeId == null ? null : params.choices.get(typeId);
        if (!(t instanceof bitwig.obf.zKO z)) { MOD.log("Filter: type isn't a choice: " + (t == null ? null : t.getClass().getName())); return false; }
        popup.MRY(z.fFs());                       // heading: the parameter's name
        Object current = z.vpw();                 // the selected option
        for (Object o : z.ODA().TMF()) {
            bitwig.obf.Dew opt = (bitwig.obf.Dew) o;
            if (opt.wGc()) continue;              // hidden option
            bitwig.obf.dKa row = bitwig.obf.dKa.cfM(opt.IwV(), (Runnable) () -> { z.cfM(opt); typeKey = null; jbi(); });
            row.eLY(opt == current ? "radio_menu_item_on.svg" : "device/" + opt.hUR()); // icon
            String d = opt.lYc();
            if (d != null && !d.isEmpty()) row.lYc(d);                                     // description
            popup.hUR(row);
        }
        return true;
    }
}
