package com.cimm.mixerpregain;

import bitwig.obf.FAu; // a device chain
import bitwig.obf.dJH; // drag start
import bitwig.obf.dJM; // drag move
import bitwig.obf.drX; // a drag
import bitwig.obf.dve; // a widget
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button
import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import com.bitwig.base.geom.SYo; // a size

/** A small Bitwig-style gain knob for a track's pre gain: drag up/down, double-click resets to 0 dB. */
final class GainKnob extends dve {
    static final iok RING = iok.cfM(70, 70, 76);
    static final iok BODY = iok.cfM(42, 42, 46);
    static final iok VALUE = iok.cfM(255, 145, 50); // Bitwig's orange
    static final iok POINTER = iok.cfM(230, 230, 230);
    static final iok TEXT = iok.cfM(170, 170, 176);

    final FAu chain;
    final Object track;
    final int height;
    private Object shownSig;
    private long lastPress;

    GainKnob(Object track, FAu chain, int height) { this.track = track; this.chain = chain; this.height = height; Poll.addKnob(this); }

    @Override public SYo mK_() { return new SYo(30, height); }
    @Override public SYo iG_() { return new SYo(60, height); }
    @Override public SYo ni_() { return new SYo(100000, height); }
    @Override public dve MJ() { return this; }

    Params.P param() { return PreGain.gain(PreGain.device(chain)); }

    double db() { Params.P p = param(); return p == null ? 0 : p.value(); }

    /** UI thread: keep the chain's pre gain in place; repaint when the value changes. */
    void tick() {
        if (track != null) PreGain.ensure(track, chain); // the mixer knob keeps the Tool in place
        PreGain.style(PreGain.device(chain));           // named Pre-Gain, collapsed
        Object sig = db();
        if (!sig.equals(shownSig)) { shownSig = sig; jbi(); }
    }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        try {
            double w = Sxo(), h = yiX(), r = Math.min(h / 2 - 3, 11), cx = Math.min(w / 2, 6 + r + 2), cy = h / 2;
            Params.P p = param();
            double lo = p == null ? -24 : p.min(), hi = p == null ? 24 : p.max(), v = db();
            double start = Math.PI * 0.75, sweep = Math.PI * 1.5;           // Bitwig's knob: 270° from lower left
            double zero = lo < 0 && hi > 0 ? (0 - lo) / (hi - lo) : 0, t = hi > lo ? (v - lo) / (hi - lo) : 0;
            g.Hnt(BODY); g.MRY(cx, cy, r); g.wGc();
            g.IwV(2.2); g.Hnt(RING);
            arc(g, cx, cy, r + 1.5, start, start + sweep);
            g.Hnt(VALUE);
            arc(g, cx, cy, r + 1.5, start + sweep * Math.min(zero, t), start + sweep * Math.max(zero, t));
            double a = start + sweep * t;
            g.Hnt(POINTER); g.IwV(1.6); g.MRY(cx + Math.cos(a) * r * 0.25, cy + Math.sin(a) * r * 0.25); g.cfM(cx + Math.cos(a) * r * 0.85, cy + Math.sin(a) * r * 0.85); g.hbP();
            String txt = (p == null ? "Pre" : String.format(v > 0 ? "+%.1f dB" : "%.1f dB", v));
            g.cfM(cx + r + 6, cy + 4, txt, TEXT);
        } catch (Throwable t) { Log.once("knob paint failed", t); }
    }

    private static void arc(com.bitwig.graphics.SYo g, double cx, double cy, double r, double a0, double a1) {
        if (a1 - a0 < 0.01) return;
        int n = (int) Math.max(4, (a1 - a0) * 12);
        for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n, x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
        g.hbP();
    }

    @Override public drX cfM(eoX button, double x, double y, eoW mods, int clicks) { drX d = press(button); return d != null ? d : super.cfM(button, x, y, mods, clicks); }
    @Override public drX MRY(eoX button, double x, double y, eoW mods, int clicks) { drX d = press(button); return d != null ? d : super.MRY(button, x, y, mods, clicks); }

    private drX press(eoX button) {
        if (!"SELECTION".equals(button.name())) return null;
        return new drX() {
            final Object undo = new Object();
            double v;
            @Override public void cfM(dJH e) {
                super.cfM(e);
                long now = System.currentTimeMillis();
                Params.P p = param();
                if (p != null && now - lastPress < 400) Log.set(p, 0, undo, "Reset pre gain"); // double-click: 0 dB
                lastPress = now;
                v = db();
                jbi();
            }
            @Override public void cfM(dJM e) {
                super.cfM(e);
                Params.P p = param();
                if (p == null) return;
                v = Math.max(p.min(), Math.min(p.max(), v - e.uWE() * 0.1)); // up: louder; 0.1 dB per pixel
                Log.set(p, v, undo, "Change pre gain");
                jbi();
            }
            @Override public boolean jQ_() { return false; }
            @Override public boolean jB_() { return true; }
        };
    }
}
