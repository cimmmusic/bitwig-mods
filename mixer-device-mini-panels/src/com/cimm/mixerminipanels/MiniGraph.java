package com.cimm.mixerminipanels;

import bitwig.obf.dJH; // drag start event
import bitwig.obf.dKW; // a popup menu
import bitwig.obf.dJM; // drag move event
import bitwig.obf.drX; // a drag in progress (Bitwig's drag handler)
import bitwig.obf.dve; // a widget
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button (SELECTION, MIDDLE, MENU, ...)
import bitwig.obf.foz; // a change listener
import bitwig.obf.hSh; // a settable parameter
import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device
import bml.api.Bml;
import com.bitwig.base.geom.SYo; // a size (width, height in UI units)

/**
 * A small graphical view of a device, drawn by us and placed under the device's row in the mixer.
 * Painting: Bitwig's graphics context (Cairo-like): Hnt(colour), MRY(x,y,w,h) rectangle, MRY(x,y) move, cfM(x,y) line,
 * MRY(x,y,r) circle, wGc() fill, hbP() stroke, IwV(w) line width, qoU()/ucQ() save/restore, lYc() clip.
 * Collapse/expand: the device's right-click menu (MiniViewActions).
 */
abstract class MiniGraph extends dve {
    static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    /** Diagnostic logging (parameters found, presses, scrolls, probes). Off: only failures are logged. */
    static final boolean DEBUG = false;

    // Colours like the reference: dark panel, blue-grey grid, light blue curve.
    static final iok BG = iok.cfM(30, 30, 32);
    static final iok GRID = iok.cfM(46, 46, 50);
    static final iok GRID_MAJOR = iok.cfM(66, 66, 72);
    static final iok CURVE = iok.cfM(150, 196, 230);
    static final iok ACCENT = iok.cfM(90, 170, 235);
    static final iok TEXT = iok.cfM(150, 190, 225);
    static final iok BAR = iok.cfM(40, 46, 54);
    static final iok NODE = iok.cfM(245, 245, 245);
    static final iok EDGE = iok.cfM(0, 0, 0);

    final jsd device;
    final Params params;
    final int fullHeight;
    private boolean collapsed;
    int place = Collapse.MIXER; // mixer or track Inspector: each keeps its own collapsed state
    private boolean listening;

    MiniGraph(jsd device, int fullHeight) {
        this.device = device;
        this.params = new Params(device);
        this.fullHeight = fullHeight;
        this.collapsed = Collapse.isCollapsed(device, place);
        Collapse.track(this);
    }

    // ---- size: stretch to the channel width, fixed height ----
    private long h() { return collapsed ? 0 : fullHeight; }
    @Override public SYo mK_() { return new SYo(30, h()); }    // minimum
    @Override public SYo iG_() { return new SYo(100, h()); }   // preferred
    @Override public SYo ni_() { return new SYo(100000, h()); } // maximum

    // ---- listen to the parameters while shown ----
    @Override public void iA_() { // attached
        super.iA_();
        listening = true;
        inPoll = true; Poll.add(this);    // checked ~60×/s only while on screen
    }

    @Override public void iz_() { // detached
        listening = false;
        inPoll = false; Poll.remove(this); // off screen (row rebuilt, scrolled away, closed): no more checks
        super.iz_();
    }

    /** A cheap fingerprint of everything we draw; Poll repaints when it changes. */
    private double lastSig = Double.NaN;
    /** Called on the UI thread about 60 times a second, outside painting. */
    void tick() {}
    boolean isCollapsed() { return collapsed; }

    /** The parameters this view draws (top-level ones; views with nested parameters add theirs). */
    java.util.List<Params.P> watched() { return params.all; }

    /** Does this view draw a modulation ghost? (VU Meter: no — its needles are already live.) */
    boolean showsModulation() { return true; }

    boolean onScreen() { return listening; }
    /** In the poll? (Also joined on first paint, in case Bitwig attached us without telling.) */
    private boolean inPoll;

    private int pollCount;
    private double choiceSig;

    void pollRepaint() {
        if (collapsed) return; // nothing drawn (expanding repaints anyway)
        try { tick(); } catch (Throwable t) { logOnce("tick failed", t); }
        double sig = 0; int i = 1;
        java.util.List<Params.P> ps = watched();
        if (showsModulation()) for (int k = 0, n = ps.size(); k < n; k++) sig += Modulation.track(ps.get(k)) * (i++ * 1.000391);
        java.util.List<Params.P> all = params.all;
        for (int k = 0, n = all.size(); k < n; k++) sig += all.get(k).base() * (i++ * 1.000173);
        if (pollCount++ % 6 == 0 && !params.choices.isEmpty()) { // choice texts (band types, modes…): ~10×/s is plenty
            double c = 0; int j = 1;
            for (String id : params.choices.keySet()) c += params.text(id).hashCode() * 1e-6 * (j++);
            choiceSig = c;
        }
        sig += choiceSig;
        if (sig != lastSig) { lastSig = sig; jbi(); }
    }

    // ---- painting ----
    volatile long lastPaint; // when we were last drawn: only views on screen take the trackpad

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        lastPaint = System.nanoTime();
        if (!inPoll) { inPoll = true; Poll.add(this); }
        double w = Sxo(), h = yiX(); // our width and height
        try {
            g.qoU(); // save
            g.MRY(0, 0, w, h); g.lYc(); // clip to us
            if (collapsed) {
                g.Hnt(BAR); g.MRY(0, 0, w, h); g.wGc();
                g.Hnt(GRID_MAJOR); g.IwV(1); // a small "expand" chevron
                double cx = w / 2;
                g.MRY(cx - 4, 2); g.cfM(cx, h - 2); g.cfM(cx + 4, 2); g.hbP();
            } else {
                g.Hnt(BG); g.MRY(0, 0, w, h); g.wGc();
                paintGraph(g, w, h);
                if (showsModulation() && modulated()) { // the modulation ghost: the same drawing with modulated values, translucent
                    ghost = true; Params.modPass = true;
                    try { paintGraph(g, w, h); } finally { ghost = false; Params.modPass = false; }
                }
                g.Hnt(EDGE); g.MRY(0, 0, 2, h); g.wGc(); g.MRY(w - 2, 0, 2, h); g.wGc(); // 2 px black edges: neighbouring channels' views read as separate
            }
            g.ucQ(); // restore
        } catch (Throwable t) {
            logOnce("paint failed", t);
        }
    }

    // ---- sticky centre line (as the Tool view's pan): catches within SNAP px, lets go past RELEASE px ----
    static final double SNAP = 10, RELEASE = 16;

    /** Updates held[0] for a pointer `off` px from the line and says whether the value should sit on it. */
    static boolean sticky(boolean[] held, double off) {
        if (Math.abs(off) < SNAP) held[0] = true;
        else if (Math.abs(off) > RELEASE) held[0] = false;
        return held[0];
    }

    /** The resonance 0…1 that puts a response at exactly 0 dB (bisection; db(r) rises with r), or NaN if none does. */
    static double resonanceForZero(java.util.function.DoubleUnaryOperator dbAt) {
        double lo = 0, hi = 1, a = dbAt.applyAsDouble(lo), b = dbAt.applyAsDouble(hi);
        if (!(a < 0 && b > 0)) return Double.NaN;
        for (int i = 0; i < 30; i++) { double m = (lo + hi) / 2; if (dbAt.applyAsDouble(m) < 0) lo = m; else hi = m; }
        return (lo + hi) / 2;
    }

    /** True while the modulation ghost is being painted. */
    boolean ghost;

    boolean modulated() {
        for (Params.P p : watched()) if (Modulation.active(p)) return true;
        return false;
    }

    /** Sets the drawing colour; in the ghost pass backgrounds and grids vanish and everything else is half see-through. */
    void col(com.bitwig.graphics.SYo g, iok c) {
        if (!ghost) { g.Hnt(c); return; }
        if (isBackground(c)) { g.Hnt(CLEAR); return; }
        g.Hnt(new iok(c.FzB(), c.IwV(), c.UNj(), c.hUR() * GHOST_ALPHA)); // iok getters: FzB red, IwV green, UNj blue, hUR alpha
    }
    static final float GHOST_ALPHA = 0.45f;
    static final iok CLEAR = new iok(0f, 0f, 0f, 0f);
    private static iok[] backgrounds;
    static boolean isBackground(iok c) {
        if (backgrounds == null) backgrounds = new iok[] {BG, GRID, GRID_MAJOR, BAR, EDGE, EqGraph.GRID_MINOR, CompGraph.FIELD, CompGraph.GRIDC,
                CompGraph.UNITY, DynGraph.BAND, ToolGraph.FIELD, ToolGraph.GRIDC, ToolGraph.MAJOR};
        for (iok b : backgrounds) if (b == c) return true;
        return false;
    }

    @Override public dve MJ() { return this; } // the widget itself, as Bitwig's widgets do

    /** A middle-button drag at (x, y), or null. */
    Dragger middle(double x, double y, double w, double h) { return null; }

    /** More rows for the right-click menu on empty space. */
    void extraMenu(dKW popup) {}

    /** True while the view should repaint every frame (e.g. a live analyser). */
    boolean animating() { return false; }

    abstract void paintGraph(com.bitwig.graphics.SYo g, double w, double h);

    /** Pressing at (x, y) with the left button (clicks: 2 = double-click): return the drag to run, or null. */
    abstract Dragger press(double x, double y, double w, double h, int clicks);

    /** Right-click at (x, y): add our own rows to Bitwig's popup and return true, or false for the usual menu. */
    boolean menu(dKW popup, double x, double y, double w, double h) { return false; }

    /** Bitwig builds a widget's right-click menu here. */
    @Override public void cfM(dKW popup, double x, double y, eoW mods) {
        try {
            if (!collapsed && menu(popup, x, y, Sxo(), yiX())) return;
        } catch (Throwable t) { MOD.log("menu failed", t); }
        super.cfM(popup, x, y, mods);
        try {
            popup.ELS(); // separator
            extraMenu(popup);
            popup.cfM("Reset Device to Default", (Runnable) () -> Reset.device(device));
            popup.cfM(collapsed ? "Expand Mini View" : "Collapse Mini View", (Runnable) () -> setCollapsed(!collapsed));
        } catch (Throwable t) { MOD.log("menu failed", t); }
    }

    /** A drag: start() runs when the press really begins (never on hover), move() for each mouse move. */
    interface Dragger {
        default void start() {}
        void move(double dx, double dy);
        /** Mouse wheel / trackpad scroll over what was pressed; return false when it isn't ours (then no wheel handling). */
        default boolean wheels() { return false; }
        default void wheel(double delta) {}
    }

    /** Bitwig only opens a widget's own right-click menu when it says it has one. */
    @Override public boolean iZ_() { return true; }

    // ---- mouse ----
    static final java.util.concurrent.atomic.AtomicInteger presses = new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger drags = new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger sets = new java.util.concurrent.atomic.AtomicInteger();

    /** Capture phase: Bitwig asks parents before children; we take presses on us here. */
    @Override public drX cfM(eoX button, double x, double y, eoW mods, int clicks) {
        drX d = handle(button, x, y, clicks);
        if (button.name().equals("SELECTION") && presses.getAndIncrement() < 60)
            if (MiniGraph.DEBUG) MOD.log("press(capture) clicks=" + clicks + " at " + (int) x + "," + (int) y + " on " + getClass().getSimpleName() + " -> " + (d == null ? "none" : "drag"));
        return d != null ? d : super.cfM(button, x, y, mods, clicks);
    }

    /** Bubble phase. */
    @Override public drX MRY(eoX button, double x, double y, eoW mods, int clicks) {
        drX d = handle(button, x, y, clicks);
        return d != null ? d : super.MRY(button, x, y, mods, clicks);
    }

    private drX handle(eoX button, double x, double y, int clicks) {
        try {
            String b = button.name();
            if (b.equals("MIDDLE") && !collapsed) { // middle-button drag on a node: Q
                Dragger d = middle(x, y, Sxo(), yiX());
                if (d != null) return new Drag(d);
            }
            if (b.equals("SELECTION") && !collapsed) {
                Dragger d = press(x, y, Sxo(), yiX(), clicks);
                if (d != null) return d.wheels() ? new WheelDrag(d) : new Drag(d);
            }
        } catch (Throwable t) {
            logOnce("press failed", t);
        }
        return null;
    }

    void setCollapsed(boolean c) { Collapse.set(device, place, c); }

    /** Called by Collapse for every live view of the device. */
    void applyCollapsed(boolean c) {
        collapsed = c;
        try { t_(!c); } catch (Throwable t) { logOnce("hide failed", t); } // shown / hidden
        try { kJ_(); } catch (Throwable ignored) {} // tell the parents our size changed
        jbi();
    }

    /** After building: where it is shown, then the remembered collapsed state for that place. */
    void init(int place) {
        this.place = place;
        collapsed = Collapse.isCollapsed(device, place);
        if (collapsed) { try { t_(false); } catch (Throwable ignored) {} }
    }

    static final java.util.concurrent.atomic.AtomicInteger wheels = new java.util.concurrent.atomic.AtomicInteger();

    /** Over a node, the wheel / trackpad adjusts it (wheelAt); elsewhere it scrolls as usual. */
    boolean wheelAt(double x, double y, double delta) { return false; }

    /** Whether the wheel at (x, y) is ours (over a node); Bitwig asks this before sending the scroll to us. */
    boolean wantsWheel(double x, double y) { return false; }

    /** Bitwig sends scrolling to the widget that answers true here; else the mixer's scroll view takes it. */
    @Override public boolean MRY(double x, double y, eoW mods, com.bitwig.base.geom.DoubleRectangle r) {
        try { if (!collapsed && wantsWheel(x, y)) return true; } catch (Throwable ignored) {}
        return super.MRY(x, y, mods, r);
    }

    static final java.util.concurrent.atomic.AtomicInteger asks = new java.util.concurrent.atomic.AtomicInteger();

    /** The check Bitwig's scroll routing (dvT) uses to pick the widget that gets a wheel / trackpad scroll. */
    @Override public boolean cfM(double x, double y, eoW mods, com.bitwig.base.geom.DoubleRectangle r) {
        boolean mine = false;
        try { mine = !collapsed && wantsWheel(x, y); } catch (Throwable ignored) {}
        if (asks.getAndIncrement() < 20) if (MiniGraph.DEBUG) MOD.log("scroll target? " + (int) x + "," + (int) y + " -> " + mine);
        return mine || super.cfM(x, y, mods, r);
    }

    /** Bitwig delivers wheel and trackpad scrolling to the widget under the mouse here. */
    @Override public void cfM(bitwig.obf.dJP e) {
        try {
            double x = e.hbP(), y = e.Grk(), dy = e.UZr(), dx = e.rGR();
            if (wheels.getAndIncrement() < 20) if (MiniGraph.DEBUG) MOD.log("wheel at " + (int) x + "," + (int) y + " rGR=" + dx + " UZr=" + dy + " " + e.getClass().getSimpleName());
            double delta = Math.abs(dy) >= Math.abs(dx) ? dy : dx;
            if (!collapsed && delta != 0 && wheelAt(x, y, delta)) { jbi(); return; }
        } catch (Throwable t) { MOD.log("wheel failed", t); }
        super.cfM(e);
    }

    /** A drag that also takes the mouse wheel: Bitwig sends wheel events to the drag handler under the mouse when it implements duW. */
    final class WheelDrag extends Drag implements bitwig.obf.duW, bitwig.obf.duX {
        WheelDrag(Dragger d) { super(d); }
        /** duX marks a drag under the pointer as scrollable: Bitwig's scroll routing then picks it (trackpad: dJR). */
        @Override public void cfM(bitwig.obf.dJR e) { cfM((bitwig.obf.dJP) e); }
        @Override public void cfM(bitwig.obf.dJP e) {
            try {
                double dy = e.UZr(), dx = e.rGR();
                if (wheels.getAndIncrement() < 20) if (MiniGraph.DEBUG) MOD.log("wheel " + e.getClass().getSimpleName() + " rGR=" + dx + " UZr=" + dy + " kFU=" + e.kFU() + " OzW=" + e.OzW());
                double delta = Math.abs(dy) >= Math.abs(dx) ? dy : dx;
                if (delta != 0) { d.wheel(delta); jbi(); }
            } catch (Throwable t) { MOD.log("wheel failed", t); }
        }
    }

    /** Bitwig's drag handler, reporting mouse movement in pixels. */
    class Drag extends drX {
        final Dragger d;
        Drag(Dragger d) { this.d = d; }
        @Override public void cfM(dJH e) { // the press really begins
            super.cfM(e);
            if (drags.getAndIncrement() < 20) if (MiniGraph.DEBUG) MOD.log("drag start");
            try { d.start(); jbi(); } catch (Throwable t) { MOD.log("drag start failed", t); }
        }
        @Override public void cfM(dJM e) { // drag moves
            super.cfM(e);
            if (drags.getAndIncrement() < 60) if (MiniGraph.DEBUG) MOD.log("drag move " + e.ejd() + "," + e.uWE());
            try { d.move(e.ejd(), e.uWE()); jbi(); } catch (Throwable t) { MOD.log("drag failed", t); }
        }
        @Override public boolean jQ_() { return false; } // a plain drag: the pointer stays visible and the dot follows it
        @Override public boolean jB_() { return true; }  // accepts drag moves
    }

    // ---- helpers ----
    static void set(Params.P p, double value, Object undoKey, String what) {
        if (p == null) return;
        double v = Math.max(p.min(), Math.min(p.max(), value));
        hSh h = p.param();
        if (sets.getAndIncrement() < 30) if (MiniGraph.DEBUG) MOD.log("set " + p.id() + " = " + v + " (was " + p.value() + ")");
        h.cfM(v, what, new com.bitwig.ramona.core.FDA(undoKey, 1500)); // set with undo; changes within 1.5 s merge into one step
    }

    static boolean loggedFail;
    static void logOnce(String what, Throwable t) {
        if (!loggedFail) { loggedFail = true; MOD.log(what, t); }
    }

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
