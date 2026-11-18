package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini view for Bitwig's "Over", after the user's mockup (2026-11-17): one yellow line — Over's Clip level — over a
 * scrolling waveform. Drag the line up / down to set Clip. As Over's own display (user's video, 2026-11-17): the
 * post-clip waveform is capped at the line — nothing crosses it — and the pre-clip waveform shows faint behind it
 * (pre-clip source still to be found: logged "Over internals:").
 *
 * Over (Bitwig 6.1.4, mod log): CLIP dB −24…+6 (the big knob), GAIN dB ±24, KNEE 0…0.49, MB_TILT ±1, MB_MIX, MIX,
 * PCM_THRESHOLD dB −6…+6 (the separate PCM clipper), choices DELTA, MAKEUP, PCM_CLIPPING.
 *
 * The level is the channel's own meter (the one Bitwig's controller API reads for VU meters): device.wYK() is the
 * channel, its mixer module MIO() → meter E3() → one tft per audio channel (IwV / cfM: peak / RMS, linear), observed
 * with a listener while the view is on screen. It is the channel's output, i.e. after Over (and anything after it).
 */
final class OverGraph extends MiniGraph {
    static final iok FIELD = iok.cfM(46, 46, 48);
    static final iok GRIDC = iok.cfM(62, 62, 66);
    static final iok WAVE = iok.cfM(220, 80, 92);        // red line
    static final iok WAVE_FILL = iok.cfM(88, 46, 54);    // dark red fill
    static final iok PRE_FILL = iok.cfM(70, 30, 38);     // pre-clip: faint dark red
    static final iok LINE = iok.cfM(240, 190, 70);       // Over's yellow
    static final double LO = -26, HI = 6.5;              // dB shown: Clip's range (−24…+6) fills the height, as Over's display
    static final int HISTORY = 240;                      // samples kept (~4 s at the poll rate)

    final Params.P clip;
    private final double[] hist = new double[HISTORY];
    private int head;
    private Object meter;                                 // the channel's meter (ljb), or null
    private final bitwig.obf.foz listener = e -> {};      // keeps the meter values flowing while we watch
    private boolean listening, loggedMeter;

    OverGraph(jsd device) {
        super(device, 110);
        Params.P c = null;
        for (Params.P p : params.all) if (p.id().equalsIgnoreCase("CONTENTS/CLIP")) c = p;
        clip = c != null ? c : params.findAny("clip");
        java.util.Arrays.fill(hist, LO);
        java.util.Arrays.fill(pre, Double.NaN);
        dumpOnce(device);
    }

    /** The level going INTO Over (its display's faint waveform): not found yet, so NaN; the log dump below is to find it. */
    private final double[] pre = new double[HISTORY];
    private static boolean dumped;

    /** Once: Over's second ID list (Guv) and its contents tree's first levels, to find where its display's data lives. */
    private static void dumpOnce(jsd device) {
        if (dumped) return;
        dumped = true;
        StringBuilder sb = new StringBuilder("Over internals:\n");
        try {
            String[] ids = device.Guv();
            if (ids != null) for (String id : ids) {
                Object v = null;
                try { v = device.MRY(id); } catch (Throwable ignored) {}
                sb.append("  Guv ").append(id).append(" -> ").append(v == null ? "null" : v.getClass().getName()).append("\n");
            }
        } catch (Throwable t) { sb.append("  Guv failed ").append(t).append("\n"); }
        try { tree(Reflect.call(device, "puu"), 1, sb, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())); }
        catch (Throwable t) { sb.append("  tree failed ").append(t).append("\n"); }
        MOD.log(sb.toString());
    }

    private static void tree(Object o, int depth, StringBuilder sb, java.util.Set<Object> seen) {
        if (o == null || depth > 4 || !seen.add(o) || sb.length() > 30000) return;
        String id = FilterGraph.str(FilterGraph.getter(o, "ax_", String.class)), nm = FilterGraph.str(FilterGraph.getter(o, "aF_", String.class));
        sb.append("  ".repeat(depth)).append(o.getClass().getName()).append(" '").append(id).append("' ").append(nm);
        if (o instanceof bitwig.obf.hSh h) { try { sb.append(" = ").append(h.iCS()); } catch (Throwable ignored) {} }
        sb.append("\n");
        if (o instanceof bitwig.obf.v3U) return;
        for (String m : FilterGraph.CHILD_LISTS) {
            Object r = FilterGraph.getter(o, m, java.util.List.class, java.util.stream.Stream.class);
            java.util.List<?> items = r instanceof java.util.List<?> l ? new java.util.ArrayList<>(l) : r instanceof java.util.stream.Stream<?> st ? st.limit(100).toList() : null;
            if (items != null) for (Object c : items) tree(c, depth + 1, sb, seen);
        }
    }


    // ---- the channel's meter ----
    private Object meter() {
        if (meter != null) return meter;
        try {
            Object ch = device.wYK(); // the channel the device is on
            if (ch instanceof com.bitwig.flt.document.core.iface.RYd r) {
                bitwig.obf.Jmw mm = r.MIO();
                if (mm != null) meter = mm.E3();
            }
        } catch (Throwable t) { if (!loggedMeter) { loggedMeter = true; MOD.log("Over: no channel meter", t); } }
        return meter;
    }

    private java.util.List<?> channels() {
        Object m = meter();
        if (!(m instanceof bitwig.obf.USG u)) return java.util.List.of();
        try { java.util.List<?> l = u.FzB(); return l == null ? java.util.List.of() : l; } catch (Throwable t) { return java.util.List.of(); }
    }

    private void listen(boolean on) {
        if (on == listening) return;
        for (Object o : channels()) {
            if (!(o instanceof bitwig.obf.xSo c)) continue;
            try { if (on) { c.Hnt(listener); c.cfM(listener); } else { c.FzB(listener); c.MRY(listener); } } catch (Throwable ignored) {}
        }
        listening = on;
    }

    @Override public void iA_() { super.iA_(); listen(true); }
    @Override public void iz_() { listen(false); super.iz_(); }

    /** The channel's level now, dB (loudest audio channel's peak). */
    private double levelDb() {
        double a = 0;
        for (Object o : channels()) {
            if (!(o instanceof bitwig.obf.xSo c)) continue;
            try { a = Math.max(a, Math.max(c.IwV(), c.cfM())); } catch (Throwable ignored) {}
        }
        return a <= 1e-6 ? LO : 20 * Math.log10(a);
    }

    @Override void tick() {
        if (!listening) listen(true);
        hist[head] = clamp(levelDb(), LO, HI + 6);
        pre[head] = Double.NaN; // no pre-clip source yet
        head = (head + 1) % HISTORY;
        jbi(); // scrolls: repaint every tick
    }

    // ---- the Clip line ----
    double clipDb() { return clip == null ? -6 : clip.value(); }
    double py(double db, double h) { return h - (db - LO) / (HI - LO) * h; }
    double dby(double y, double h) { return LO + (h - y) / h * (HI - LO); }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        double ly = Math.floor(py(clipDb(), h)) + 0.5;
        if (!ghost) {
            col(g, FIELD); g.MRY(0, 0, w, h); g.wGc();
            g.IwV(1);
            col(g, GRIDC);                                               // the mockup's grid: 10 across, 5 down
            for (int i = 1; i < 10; i++) { double x = Math.floor(w * i / 10) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
            for (int i = 1; i < 5; i++) { double y = Math.floor(h * i / 5) + 0.5; g.MRY(0, y); g.cfM(w, y); g.hbP(); }

            // newest at the right; post-clip capped at the line (nothing crosses it), pre-clip faint behind
            int n = HISTORY;
            double step = w / (n - 1), c = clipDb();
            g.qoU(); g.MRY(0, 0, w, h); g.lYc();
            col(g, PRE_FILL);                                            // pre: the full signal, faint
            for (int i = 0; i < n - 1; i++) {
                double d = pre[(head + i) % n];
                if (Double.isNaN(d)) continue;
                double y = py(d, h);
                g.MRY(i * step, y, step + 0.6, h - y); g.wGc();
            }
            col(g, WAVE_FILL);                                           // post: capped at the line
            for (int i = 0; i < n - 1; i++) {
                double y = py(Math.min(hist[(head + i) % n], c), h);
                g.MRY(i * step, y, step + 0.6, h - y); g.wGc();
            }
            g.IwV(1.4);
            col(g, WAVE);
            for (int i = 1; i < n; i++) {
                double d0 = Math.min(hist[(head + i - 1) % n], c), d1 = Math.min(hist[(head + i) % n], c);
                g.MRY((i - 1) * step, py(d0, h)); g.cfM(i * step, py(d1, h)); g.hbP();
            }
            g.ucQ();
        }
        col(g, LINE); g.IwV(2); g.MRY(0, ly); g.cfM(w, ly); g.hbP();    // Clip
    }


    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        if (clip == null || Math.abs(y - py(clipDb(), h)) > 8) return (ddx, ddy) -> {}; // grab the line anywhere along it
        final Object undo = new Object();
        final double[] pos = {py(clipDb(), h)};
        return (ddx, ddy) -> {
            pos[0] += ddy;
            set(clip, clamp(dby(pos[0], h), clip.min(), clip.max()), undo, "Change clip");
        };
    }
}
