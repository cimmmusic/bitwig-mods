package com.cimm.groove;

import bitwig.obf.dJH; // drag start event
import bitwig.obf.dJM; // drag move event
import bitwig.obf.drX; // a drag in progress
import bitwig.obf.dve; // a widget
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The GROOVE section's own rows, drawn to the Play menu's measurements (UI units): rows 48 apart, a 48-wide icon/knob
 * column, labels from x 61, 40-high button groups ending 49 before the right edge (the pin column), the same greys and
 * orange. Each row is one widget that lays out and hit-tests its parts.
 *
 * Graphics (Cairo-like): Hnt(colour), MRY(x,y,w,h) rectangle, MRY(x,y) move, cfM(x,y) line, wGc() fill, hbP() stroke,
 * IwV(w) line width, qoU()/ucQ() save/restore, lYc() clip, FzB(x,y) translate, Hnt(sx,sy) scale, cfM(x,y,text,colour)
 * text at a baseline, cfM(text).IwV() text advance, iHb().FzB() font height.
 */
final class Widgets {
    private Widgets() {}

    // ---- the Play menu's measurements ----
    static final double ROW = 45;        // a row's height (rows sit 48 apart with the section's gap)
    static final double ICON = 48;       // the icon / knob column
    static final double LABEL_X = 61;    // where row labels start
    static final double PIN = 49;        // the pin column on the right, kept free
    static final double CTRL = 40;       // button groups and fields
    static final double FONT = 18;       // label text size

    // ---- colours sampled from the Play menu ----
    static final iok PANEL = iok.cfM(63, 63, 63);
    static final iok BUTTON = iok.cfM(90, 90, 90);
    static final iok BUTTON_TOP = iok.cfM(100, 100, 100);
    static final iok OUTLINE = iok.cfM(40, 40, 40);
    static final iok ORANGE = iok.cfM(239, 141, 57);
    static final iok TEXT = iok.cfM(225, 225, 225);
    static final iok TEXT_DIM = iok.cfM(150, 150, 150);
    static final iok TEXT_ON = iok.cfM(40, 40, 40);
    static final iok RING = iok.cfM(80, 80, 80);
    static final iok KNOB_DARK = iok.cfM(36, 36, 36);
    static final iok KNOB_LIGHT = iok.cfM(88, 88, 88);
    static final iok POINTER = iok.cfM(235, 235, 235);
    static final iok WARN = iok.cfM(240, 128, 105);

    // ------------------------------------------------------------------------------------------------ text

    private static double defaultFontH = Double.NaN;
    private static boolean loggedFont;

    /** Scale that brings the default UI font to the Play menu's label size. */
    static double textScale(com.bitwig.graphics.SYo g) {
        if (Double.isNaN(defaultFontH)) {
            try { defaultFontH = g.iHb().FzB(); } catch (Throwable t) { defaultFontH = 15; }
            if (!(defaultFontH > 4)) defaultFontH = 15;
            if (!loggedFont) { loggedFont = true; State.MOD.log("UI font height " + defaultFontH); }
        }
        return FONT * 1.2 / defaultFontH;
    }

    static double textW(com.bitwig.graphics.SYo g, String s) {
        double w;
        try { w = g.cfM(s).IwV(); } catch (Throwable t) { w = s.length() * 7; }
        return w * textScale(g);
    }

    /** Text with its left end at x, vertically centred on cy. */
    static void text(com.bitwig.graphics.SYo g, String s, double x, double cy, iok c) {
        double k = textScale(g);
        g.qoU();
        try {
            g.FzB(x, cy + FONT * 0.36); // translate to the baseline
            g.Hnt(k, k);                // scale
            g.cfM(0, 0, s, c);
        } finally { g.ucQ(); }
    }

    static void centred(com.bitwig.graphics.SYo g, String s, double x, double w, double cy, iok c) {
        s = fit(g, s, w - 8);
        text(g, s, x + (w - textW(g, s)) / 2, cy, c);
    }

    static void rightAligned(com.bitwig.graphics.SYo g, String s, double right, double cy, iok c) {
        text(g, s, right - textW(g, s), cy, c);
    }

    static String fit(com.bitwig.graphics.SYo g, String s, double w) {
        if (textW(g, s) <= w) return s;
        while (s.length() > 1 && textW(g, s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ------------------------------------------------------------------------------------------------ shapes

    /** A rounded rectangle path (corner radius r). */
    static void rounded(com.bitwig.graphics.SYo g, double x, double y, double w, double h, double r) {
        r = Math.min(r, Math.min(w, h) / 2);
        g.MRY(x + r, y);
        g.cfM(x + w - r, y); arcTo(g, x + w - r, y + r, r, -Math.PI / 2, 0);
        g.cfM(x + w, y + h - r); arcTo(g, x + w - r, y + h - r, r, 0, Math.PI / 2);
        g.cfM(x + r, y + h); arcTo(g, x + r, y + h - r, r, Math.PI / 2, Math.PI);
        g.cfM(x, y + r); arcTo(g, x + r, y + r, r, Math.PI, Math.PI * 1.5);
    }

    private static void arcTo(com.bitwig.graphics.SYo g, double cx, double cy, double r, double a, double b) {
        int n = Math.max(2, (int) Math.ceil(Math.abs(b - a) / 0.25));
        for (int i = 1; i <= n; i++) { double t = a + (b - a) * i / n; g.cfM(cx + Math.cos(t) * r, cy + Math.sin(t) * r); }
    }

    /** An arc as a path (from a to b, radians, clockwise on screen). */
    static void arc(com.bitwig.graphics.SYo g, double cx, double cy, double r, double a, double b) {
        int n = Math.max(2, (int) Math.ceil(Math.abs(b - a) / 0.08));
        for (int i = 0; i <= n; i++) {
            double t = a + (b - a) * i / n, x = cx + Math.cos(t) * r, y = cy + Math.sin(t) * r;
            if (i == 0) g.MRY(x, y); else g.cfM(x, y);
        }
    }

    static void disc(com.bitwig.graphics.SYo g, double cx, double cy, double r, iok c) {
        g.Hnt(c); arc(g, cx, cy, r, 0, Math.PI * 2); g.wGc();
    }

    /** A group of buttons like Latch | Touch | Write: dark outline, 2-unit separators, one lit orange. */
    static void segments(com.bitwig.graphics.SYo g, String[] names, int on, double x, double y, double w, double h) {
        g.Hnt(OUTLINE); rounded(g, x, y, w, h, 4); g.wGc();
        int n = names.length;
        double inner = w - 2, bw = inner / n;
        for (int i = 0; i < n; i++) {
            double bx = x + 1 + Math.round(i * bw), bx2 = x + 1 + Math.round((i + 1) * bw) - (i < n - 1 ? 2 : 0);
            boolean lit = i == on;
            g.Hnt(lit ? ORANGE : BUTTON);
            rounded(g, bx, y + 1, bx2 - bx, h - 2, i == 0 || i == n - 1 ? 3 : 0.01); g.wGc();
            if (!lit) { g.Hnt(BUTTON_TOP); g.MRY(bx + 2, y + 1, bx2 - bx - 4, 1); g.wGc(); } // the faint top light
            centred(g, names[i], bx, bx2 - bx, y + h / 2, lit ? TEXT_ON : TEXT);
        }
    }

    static int segmentAt(double px, int n, double x, double w) {
        return (int) Math.max(0, Math.min(n - 1, Math.floor((px - x) / (w / n))));
    }

    /** A knob like the design: grey ring with the value in orange, dark edge, shaded body, white pointer bar. */
    static void knob(com.bitwig.graphics.SYo g, double cx, double cy, double v, double min, double max) {
        double r = 21, a0 = Math.toRadians(135), sweep = Math.toRadians(270);
        double to = (v - min) / (max - min), from = min < 0 ? -min / (max - min) : 0;
        g.IwV(4);
        g.Hnt(RING_GREY); arc(g, cx, cy, r, a0, a0 + sweep); g.hbP();
        if (Math.abs(to - from) > 0.002) {
            g.Hnt(ORANGE); arc(g, cx, cy, r, a0 + sweep * Math.min(from, to), a0 + sweep * Math.max(from, to)); g.hbP();
        }
        disc(g, cx, cy, 16, EDGE);
        for (int i = 0; i < 6; i++) { // shaded body: lighter at the top
            double t = i / 5.0;
            int c = (int) Math.round(lerp(112, 74, t));
            g.qoU();
            try {
                arc(g, cx, cy, 15, 0, Math.PI * 2); g.lYc();
                g.Hnt(iok.cfM(c, c, c)); g.MRY(cx - 15, cy - 15 + 30 * i / 6.0, 30, 30 / 6.0 + 1); g.wGc();
            } finally { g.ucQ(); }
        }
        double a = a0 + sweep * to, ca = Math.cos(a), sa = Math.sin(a);
        g.Hnt(POINTER); g.IwV(3.5);
        g.MRY(cx + ca * 7, cy + sa * 7); g.cfM(cx + ca * 13, cy + sa * 13); g.hbP();
    }

    static final iok RING_GREY = iok.cfM(96, 96, 96);

    static String percent(double v) { return Math.round(v * 100) + "%"; }

    // ------------------------------------------------------------------------------------------------ widgets

    /** A painted widget that stretches across the section and takes left clicks and drags. */
    abstract static class Base extends dve {
        final double prefW, h;
        final boolean stretch;
        Base(double prefW, double h, boolean stretch) { this.prefW = prefW; this.h = h; this.stretch = stretch; }
        @Override public SYo mK_() { return new SYo((long) (stretch ? 200 : prefW), (long) h); }
        @Override public SYo iG_() { return new SYo((long) prefW, (long) h); }
        @Override public SYo ni_() { return new SYo(stretch ? 100000 : (long) prefW, (long) h); }
        @Override public dve MJ() { return this; }

        @Override public void Hnt(com.bitwig.graphics.SYo g) {
            super.Hnt(g);
            try {
                g.qoU();
                g.MRY(0, 0, Sxo(), yiX()); g.lYc();
                paint(g, Sxo(), yiX());
            } catch (Throwable t) { State.MOD.log("paint failed: " + getClass().getSimpleName(), t); }
            finally { try { g.ucQ(); } catch (Throwable ignored) {} }
        }

        abstract void paint(com.bitwig.graphics.SYo g, double w, double h);

        /** A left press at (x, y): what happens when it really starts (never on hover), and drag moves (incremental). */
        Runnable click(double x, double y, int clicks) { return null; }
        DoubleConsumer drag(double x, double y) { return null; }

        @Override public drX cfM(eoX button, double x, double y, eoW mods, int clicks) {
            drX d = handle(button, x, y, clicks);
            return d != null ? d : super.cfM(button, x, y, mods, clicks);
        }

        @Override public drX MRY(eoX button, double x, double y, eoW mods, int clicks) {
            drX d = handle(button, x, y, clicks);
            return d != null ? d : super.MRY(button, x, y, mods, clicks);
        }

        private drX handle(eoX button, double x, double y, int clicks) {
            try {
                if (!button.name().equals("SELECTION")) return null;
                Runnable r = click(x, y, clicks);
                DoubleConsumer dy = drag(x, y);
                if (r == null && dy == null) return null;
                return new drX() {
                    @Override public void cfM(dJH e) { super.cfM(e); if (r != null) try { r.run(); } catch (Throwable t) { State.MOD.log("click failed", t); } jbi(); }
                    @Override public void cfM(dJM e) { super.cfM(e); if (dy != null) try { dy.accept(e.uWE()); } catch (Throwable t) { State.MOD.log("drag failed", t); } jbi(); }
                    @Override public boolean jQ_() { return false; }
                    @Override public boolean jB_() { return dy != null; }
                };
            } catch (Throwable t) { State.MOD.log("press failed", t); return null; }
        }
    }

    /** Empty space. */
    static final class Spacer extends Base {
        Spacer(double w, double h) { super(w, h, false); }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {}
    }

    // ---- the design's measurements (Desktop/menu design.png) ----
    static final double SEG_X = 106, SEG_W = 290;         // Source switch
    static final double FIELD_H = 38, SMALL_BTN = 38, BROWSE_W = 118, GAP = 8;
    static final double KNOB_ROW = 70, KNOB_GAP = 34;     // knob grid: row pitch, space after a label
    static final double BASE_X = 64, BASE_BTN = 66, BASE_GAP = 14;

    /** Vertical shading like the design's buttons (lighter top, darker bottom), as thin bands. */
    static void shaded(com.bitwig.graphics.SYo g, double x, double y, double w, double h, double r, iok top, iok bottom) {
        g.qoU();
        try {
            rounded(g, x, y, w, h, r); g.lYc(); // clip to the button
            int n = 8;
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                g.Hnt(iok.cfM((int) Math.round(lerp(top.FzB(), bottom.FzB(), t) * 255), (int) Math.round(lerp(top.IwV(), bottom.IwV(), t) * 255),
                        (int) Math.round(lerp(top.UNj(), bottom.UNj(), t) * 255)));
                g.MRY(x, y + h * i / n, w, h / n + 1); g.wGc();
            }
        } finally { g.ucQ(); }
    }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static final iok BTN_TOP = iok.cfM(98, 98, 98), BTN_BOTTOM = iok.cfM(85, 85, 85);
    static final iok FIELD_TOP = iok.cfM(72, 72, 72), FIELD_BOTTOM = iok.cfM(57, 57, 57);
    static final iok EDGE = iok.cfM(43, 43, 43);

    /** A design button: dark edge, shaded grey (or Bitwig orange when lit), centred label. */
    static void button(com.bitwig.graphics.SYo g, double x, double y, double w, double h, String label, boolean lit) {
        g.Hnt(EDGE); rounded(g, x, y, w, h, 5); g.wGc();
        if (lit) { g.Hnt(ORANGE); rounded(g, x + 1, y + 1, w - 2, h - 2, 4); g.wGc(); }
        else shaded(g, x + 1, y + 1, w - 2, h - 2, 4, BTN_TOP, BTN_BOTTOM);
        if (label != null) centred(g, label, x, w, y + h / 2, lit ? TEXT_ON : TEXT);
    }

    /** "Source  [Bitwig | Groove File]". */
    static final class SourceRow extends Base {
        final IntSupplier get; final IntConsumer set;
        static final String[] NAMES = {"Bitwig", "Groove File"};
        SourceRow(IntSupplier get, IntConsumer set) { super(420, 52, true); this.get = get; this.set = set; }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            double y = (h - CTRL) / 2;
            text(g, "Source", 0, h / 2, TEXT);
            int on = get.getAsInt();
            g.Hnt(EDGE); rounded(g, SEG_X, y, SEG_W, CTRL, 5); g.wGc();
            double half = SEG_W / 2;
            for (int i = 0; i < 2; i++) {
                double bx = SEG_X + 1 + i * half, bw = half - (i == 0 ? 2 : 2);
                boolean lit = i == on;
                if (lit) { g.Hnt(ORANGE); rounded(g, bx, y + 1, bw, CTRL - 2, 4); g.wGc(); }
                else shaded(g, bx, y + 1, bw, CTRL - 2, 4, BTN_TOP, BTN_BOTTOM);
                centred(g, NAMES[i], bx, bw, y + CTRL / 2, lit ? TEXT_ON : TEXT);
            }
        }
        @Override Runnable click(double x, double y, int clicks) {
            if (x < SEG_X || x > SEG_X + SEG_W) return null;
            int i = x < SEG_X + SEG_W / 2 ? 0 : 1;
            return () -> set.accept(i);
        }
    }

    /** One knob of the grid. */
    static final class K {
        final String name; final double min, max, dflt; final DoubleSupplier get; final DoubleConsumer set;
        K(String name, double min, double max, double dflt, DoubleSupplier get, DoubleConsumer set) {
            this.name = name; this.min = min; this.max = max; this.dflt = dflt; this.get = get; this.set = set;
        }
    }

    /** Knobs with their names to the right, flowing left to right and onto a second row (4 + 1 in the design). */
    static final class KnobGrid extends Base {
        final K[] knobs;
        private final double[] kx = new double[8], ky = new double[8];
        private double held;
        private K dragging;
        KnobGrid(K... knobs) { super(560, KNOB_ROW * 2, true); this.knobs = knobs; }

        /** Positions from the width and the label widths (needs the graphics context, so done while painting). */
        private void layout(com.bitwig.graphics.SYo g, double w) {
            double x = 0, y = KNOB_ROW / 2;
            for (int i = 0; i < knobs.length; i++) {
                double need = 48 + 18 + textW(g, knobs[i].name);
                if (x > 0 && x + need > w) { x = 0; y += KNOB_ROW; }
                kx[i] = x + 24; ky[i] = y;
                x += need + KNOB_GAP;
            }
        }

        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            layout(g, w);
            for (int i = 0; i < knobs.length; i++) {
                K k = knobs[i];
                double v = k.get.getAsDouble();
                knob(g, kx[i], ky[i], v, k.min, k.max);
                text(g, k.name, kx[i] + 24 + 18, ky[i], TEXT);
                if (k == dragging) text(g, percent(v), kx[i] + 24 + 18 + textW(g, k.name) + 8, ky[i], TEXT_DIM);
            }
        }

        private K at(double x, double y) {
            for (int i = 0; i < knobs.length; i++) if (Math.hypot(x - kx[i], y - ky[i]) <= 26) return knobs[i];
            return null;
        }

        @Override Runnable click(double x, double y, int clicks) {
            K k = at(x, y);
            if (k == null) return null;
            if (clicks >= 2) return () -> k.set.accept(k.dflt);
            return () -> { held = k.get.getAsDouble(); dragging = k; lastSet = System.nanoTime(); };
        }

        @Override DoubleConsumer drag(double x, double y) {
            K k = at(x, y);
            if (k == null) return null;
            return dy -> { // 200 units of travel for the full range
                dragging = k; lastSet = System.nanoTime();
                held = Math.max(k.min, Math.min(k.max, held - dy / 200.0 * (k.max - k.min)));
                k.set.accept(Math.round(held * 100) / 100.0);
            };
        }

        /** The value shows while dragging; the next repaint after the drag hides it. */
        void endDrag() { dragging = null; jbi(); }
        @Override public void Hnt(com.bitwig.graphics.SYo g) {
            if (dragging != null && System.nanoTime() - lastSet > 1_200_000_000L) dragging = null;
            super.Hnt(g);
        }
        long lastSet;
    }

    /** "Base  [1/4] [1/8] [1/8T] [1/16] [1/16T] [1/32]" as separate buttons, the current one lit. */
    static final class BaseRow extends Base {
        final IntSupplier get; final IntConsumer set;
        BaseRow(IntSupplier get, IntConsumer set) { super(BASE_X + 6 * BASE_BTN + 5 * BASE_GAP, 60, true); this.get = get; this.set = set; }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            double y = h - CTRL - 6;
            text(g, "Base", 0, y + CTRL / 2, TEXT);
            int on = get.getAsInt();
            for (int i = 0; i < Groove.BASE_NAMES.length; i++)
                button(g, BASE_X + i * (BASE_BTN + BASE_GAP), y, BASE_BTN, CTRL, Groove.BASE_NAMES[i], i == on);
        }
        @Override Runnable click(double x, double y, int clicks) {
            for (int i = 0; i < Groove.BASE_NAMES.length; i++) {
                double bx = BASE_X + i * (BASE_BTN + BASE_GAP);
                if (x >= bx && x <= bx + BASE_BTN) { int at = i; return () -> set.accept(at); }
            }
            return null;
        }
    }

    /** The groove's name in a dark chooser field with a small ▾ (the menu button around it opens the list). */
    static final class NameField extends Base {
        NameField() { super(300, FIELD_H, true); }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            g.Hnt(EDGE); rounded(g, 0, 0, w, h, 3); g.wGc();
            shaded(g, 1, 1, w - 2, h - 2, 2, FIELD_TOP, FIELD_BOTTOM);
            String msg = State.message;
            State.Entry e = State.selected();
            String s = msg != null ? msg : e == null ? "No groove loaded: click Browse" : e.groove.name;
            text(g, fit(g, s, w - 46), 16, h / 2, msg != null ? WARN : TEXT);
            double cx = w - 18, cy = h / 2;
            g.Hnt(TEXT); g.MRY(cx - 4, cy - 2); g.cfM(cx + 4, cy - 2); g.cfM(cx, cy + 3); g.wGc(); // ▾
        }
    }

    /** ◀ ▶ Browse as separate buttons, 8 apart. */
    static final class Buttons extends Base {
        final Runnable prev, next, browse;
        Buttons(Runnable prev, Runnable next, Runnable browse) {
            super(SMALL_BTN * 2 + BROWSE_W + GAP * 2, FIELD_H, false); this.prev = prev; this.next = next; this.browse = browse;
        }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            button(g, 0, 0, SMALL_BTN, h, null, false);
            button(g, SMALL_BTN + GAP, 0, SMALL_BTN, h, null, false);
            button(g, (SMALL_BTN + GAP) * 2, 0, BROWSE_W, h, "Browse", false);
            double cy = h / 2, c1 = SMALL_BTN / 2, c2 = SMALL_BTN + GAP + SMALL_BTN / 2;
            g.Hnt(TEXT);
            g.MRY(c1 + 3, cy - 5); g.cfM(c1 + 3, cy + 5); g.cfM(c1 - 4, cy); g.wGc(); // ◀
            g.MRY(c2 - 3, cy - 5); g.cfM(c2 - 3, cy + 5); g.cfM(c2 + 4, cy); g.wGc(); // ▶
        }
        @Override Runnable click(double x, double y, int clicks) {
            if (x < SMALL_BTN) return prev;
            if (x >= SMALL_BTN + GAP && x < SMALL_BTN * 2 + GAP) return next;
            if (x >= (SMALL_BTN + GAP) * 2) return browse;
            return null;
        }
    }

    /** Every painted widget currently shown (for repaints after a change). */
    static final List<dve> shown = new ArrayList<>();
    static <T extends dve> T track(T w) { shown.add(w); return w; }
}
