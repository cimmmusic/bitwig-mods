package com.cimm.groove;

import bitwig.obf.AQw; // a boolean value a native button shows and sets (8 plainly named methods)
import bitwig.obf.LJt; // a value a native label shows (6 plainly named methods)
import bitwig.obf.aoA; // Bitwig's knob
import bitwig.obf.dAd; // Bitwig's button
import bitwig.obf.dJH; // drag start event
import bitwig.obf.dJM; // drag move event
import bitwig.obf.drX; // a drag in progress
import bitwig.obf.dve; // a widget
import bitwig.obf.dzg; // button base: x3t() content, TMF the standard button style, Hnt(Runnable) click action
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button
import bitwig.obf.foz; // a change listener
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Bitwig's own widgets driven by the mod's settings: labels and buttons take small value interfaces (LJt, AQw) that
 * the mod implements; the knob is Bitwig's knob class drawing our value with the theme's colours.
 */
final class Native {
    private Native() {}

    /** Listeners for one value; fire() tells the widgets bound to it to update. */
    abstract static class Value {
        private final List<foz> listeners = new CopyOnWriteArrayList<>();
        public boolean isObservable() { return true; }
        public boolean isSettable() { return true; }
        public void addListener(foz l) { listeners.add(l); }
        public void removeListener(foz l) { listeners.remove(l); }
        private Object last;
        /** Tells the bound widgets the value changed, with Bitwig's change event (source, old, new). */
        void fire() {
            Object v = value(), old = last;
            last = v;
            bitwig.obf.VGL e = new bitwig.obf.VGL((bitwig.obf.rMe) this, old, v);
            for (foz l : listeners) try { l.eventOccurred(e); } catch (Throwable t) { State.MOD.log("widget update failed", t); }
        }
        abstract Object value();
    }

    /** Text a native label shows (the groove name, a knob's name with its value while dragging). */
    static final class Text extends Value implements LJt {
        final Supplier<String> text;
        Text(Supplier<String> text) { this.text = text; }
        @Override Object value() { return text.get(); }
        @Override public Object getValue() { return text.get(); }
        @Override public Object get() { return text.get(); }
        @Override public void setValue(Object v) {}
    }

    /** On/off for a native button: lit when get() is true; pressing calls press(). */
    static final class Bool extends Value implements AQw {
        final BooleanSupplier get; final Runnable press;
        Bool(BooleanSupplier get, Runnable press) { this.get = get; this.press = press; }
        @Override Object value() { return get.getAsBoolean(); }
        @Override public Object getValue() { return get.getAsBoolean(); }
        @Override public boolean getBoolValue() { return get.getAsBoolean(); }
        @Override public void setBoolValue(boolean b) { act(); }
        @Override public void setValue(Object v) { act(); }
        private void act() {
            try { press.run(); } catch (Throwable t) { State.MOD.log("button failed", t); }
            fire(); // a lit option pressed again stays lit
        }
    }

    // ------------------------------------------------------------------------------------------------ buttons

    /** Bitwig's standard text button at a fixed width (its own height). */
    static final class Button extends dAd {
        final long width;
        Button(String text, long width) {
            super();
            this.width = width;
            Hnt(dzg.x3t(), dzg.TMF); // as dAd.gG(): standard button content and style
            G_();
            IwV(text);
        }
        @Override public SYo mK_() { return new SYo(width, super.mK_().IwV()); }
        @Override public SYo iG_() { return new SYo(width, super.iG_().IwV()); }
        @Override public SYo ni_() { return new SYo(width, super.ni_().IwV()); }
    }

    /** A button that runs an action. */
    static Button action(String text, long width, Runnable r) {
        Button b = new Button(text, width);
        b.Hnt(r);
        return b;
    }

    /** A button lit while on.get() is true; pressing runs press. */
    static Button option(String text, long width, Bool on) {
        Button b = new Button(text, width);
        try { b.MRY(b.LIK(), on); } catch (Throwable t) { State.MOD.log("button binding failed", t); }
        return b;
    }

    // ------------------------------------------------------------------------------------------------ knob

    /** Bitwig's knob (small, as Shuffle's) drawing our value; drag up/down, double-click resets. */
    static final class Knob extends aoA {
        final double min, max, dflt; final DoubleSupplier get; final DoubleConsumer set; final Runnable dragging;
        private double held;
        private long lastPress;

        Knob(double min, double max, double dflt, DoubleSupplier get, DoubleConsumer set, Runnable dragging) {
            super();
            this.min = min; this.max = max; this.dflt = dflt; this.get = get; this.set = set; this.dragging = dragging;
            this.popup = new Text(() -> this.name + "  " + percent(this.get.getAsDouble()) + " %   (default " + percent(defaultValue()) + " %)");
            // as aoA.tp2() and the Play menu's knob rows
            bitwig.obf.eiH look = bitwig.obf.dpN.TVZ().Pus();
            MRY(look.Mph().IwV(), look.FCz().IwV(), false);
            G_();
            try {
                MRY(bitwig.obf.trK.cfM());
                E1b().MRY("small");
                a_(bitwig.obf.w3.qoU);
                Hnt("");
                E3(1f);
            } catch (Throwable t) { State.MOD.log("knob style failed", t); }
        }

        /** The default a double-click returns to (may depend on the source, as Velocity's does). */
        java.util.function.DoubleSupplier defaultBy;
        double defaultValue() { return defaultBy != null ? defaultBy.getAsDouble() : dflt; }

        double normalised() { return Math.max(0, Math.min(1, (get.getAsDouble() - min) / (max - min))); }

        /** Bitwig paints the knob here. Unbound, it would draw it inactive (value 0, faded colour): we give it our value,
         *  the knob style's own value colour (the theme's orange) and the active state. */
        @Override public void cfM(com.bitwig.graphics.SYo g, iok colour, double value, boolean flag) {
            iok c = colour;
            try { c = E1b().FCz().cfM(this); } catch (Throwable t) { if (!loggedColour) { loggedColour = true; State.MOD.log("knob colour from theme failed", t); } }
            super.cfM(g, c, normalised(), false);
        }
        private static boolean loggedColour;

        /**
         * The knob's face, drawn here: Bitwig's own knob drawing reads the value from a bound parameter, which this knob
         * hasn't got, so it stays empty. Same size as Bitwig's: grey ring, the value as an orange arc (from the middle for
         * a knob that goes both ways), dark body, white pointer.
         */
        @Override public void Hnt(com.bitwig.graphics.SYo g) {
            try {
                double w = Sxo(), h = yiX();
                double cx = w / 2, cy = h / 2, r = Math.max(6, Math.min(w, h) / 2 - 2.5);
                double a0 = Math.toRadians(135), sweep = Math.toRadians(270);
                double to = normalised(), from = min < 0 ? -min / (max - min) : 0;
                iok orange = Widgets.ORANGE;
                g.qoU();
                try {
                    g.IwV(Math.max(2.5, r * 0.2));
                    g.Hnt(RING); Widgets.arc(g, cx, cy, r, a0, a0 + sweep); g.hbP();
                    if (Math.abs(to - from) > 0.002) {
                        g.Hnt(orange); Widgets.arc(g, cx, cy, r, a0 + sweep * Math.min(from, to), a0 + sweep * Math.max(from, to)); g.hbP();
                    }
                    double body = r * 0.72;
                    Widgets.disc(g, cx, cy, body + 1, EDGE);
                    for (int i = 0; i < 6; i++) { // shaded body, lighter at the top
                        int c = (int) Math.round(112 - (112 - 70) * i / 5.0);
                        g.qoU();
                        try {
                            Widgets.arc(g, cx, cy, body, 0, Math.PI * 2); g.lYc();
                            g.Hnt(iok.cfM(c, c, c)); g.MRY(cx - body, cy - body + 2 * body * i / 6.0, 2 * body, 2 * body / 6.0 + 1); g.wGc();
                        } finally { g.ucQ(); }
                    }
                    double a = a0 + sweep * to;
                    g.Hnt(POINTER); g.IwV(Math.max(2, r * 0.16));
                    g.MRY(cx + Math.cos(a) * body * 0.35, cy + Math.sin(a) * body * 0.35);
                    g.cfM(cx + Math.cos(a) * body * 0.85, cy + Math.sin(a) * body * 0.85); g.hbP();
                } finally { g.ucQ(); }
            } catch (Throwable t) { if (!loggedColour) { loggedColour = true; State.MOD.log("knob paint failed", t); } }
        }
        static final iok RING = iok.cfM(96, 96, 96), EDGE = iok.cfM(36, 36, 36), POINTER = iok.cfM(235, 235, 235);

        /** The value as Bitwig's drag popup shows it (e.g. "Timing 80 %"). */
        String name = "";
        /** Whole percents, or tenths when not whole. */
        static String percent(double v) {
            double p = v * 100;
            return Math.abs(p - Math.round(p)) < 0.05 ? Long.toString(Math.round(p)) : String.format(java.util.Locale.ROOT, "%.1f", p);
        }
        Text popup;

        @Override public drX cfM(eoX button, double x, double y, eoW mods, int clicks) {
            drX d = press(button, clicks);
            return d != null ? d : super.cfM(button, x, y, mods, clicks);
        }

        @Override public drX MRY(eoX button, double x, double y, eoW mods, int clicks) {
            drX d = press(button, clicks);
            return d != null ? d : super.MRY(button, x, y, mods, clicks);
        }

        private static int pressLogs;
        private drX press(eoX button, int clicks) {
            try {
                if (pressLogs++ < 12) State.MOD.log("knob " + name + ": press " + button.name() + " clicks " + clicks);
                if (!button.name().equals("SELECTION")) return null;
                return new drX() {
                    boolean moved;
                    @Override public void cfM(dJH e) {
                        super.cfM(e);
                        held = get.getAsDouble();
                        jbi();
                    }
                    /** Released: a click without a drag; two within 400 ms reset the knob (Bitwig reports neither a
                     *  click count nor a drag start without movement, so clicks are counted here). */
                    @Override public void cfM(bitwig.obf.dJI e) {
                        super.cfM(e);
                        if (moved) return;
                        long now = System.currentTimeMillis();
                        if (now - lastPress < 400) { set.accept(defaultValue()); lastPress = 0; State.MOD.log("knob " + name + " reset to " + defaultValue()); jbi(); }
                        else lastPress = now;
                    }
                    @Override public void cfM(dJM e) {
                        super.cfM(e);
                        moved = true;
                        try { // 200 units of travel for the full range
                            double fine = Graph.shift(e) ? 0.1 : 1; // Shift: a tenth of the speed
                            held = Math.max(min, Math.min(max, held - e.uWE() / 200.0 * (max - min) * fine));
                            set.accept(Math.round(held * 1000) / 1000.0);
                            popup.fire();
                            if (dragging != null) dragging.run();
                        } catch (Throwable t) { State.MOD.log("knob drag failed", t); }
                        jbi();
                    }
                    /** Bitwig's own value popup during the drag shows this. */
                    @Override public LJt a_(eoW mods) { popup.fire(); return popup; }
                    @Override public boolean jQ_() { return false; }
                    @Override public boolean jB_() { return true; }
                };
            } catch (Throwable t) { State.MOD.log("knob press failed", t); return null; }
        }
    }

    /** Bitwig's menu button with the standard button content (no arrow in front of the text), stretching across the
     *  row; the ▾ is drawn at its right end, inside the box. */
    static final class Chooser extends bitwig.obf.dLh {
        Chooser() {
            super();
            MRY(dzg.x3t(), dzg.TMF);
            G_();
        }
        void text(String s) { try { gfw().IwV(s); } catch (Throwable t) { State.MOD.log("chooser text failed", t); } }
        /** Shows the selected groove's name (or a message). */
        void refresh() {
            String msg = State.message;
            State.Entry e = State.selected();
            text(msg != null ? msg : e == null ? "No groove loaded: click Browse" : e.groove.name);
        }
        @Override public SYo mK_() { return new SYo(120, super.mK_().IwV()); }
        @Override public SYo iG_() { return new SYo(300, super.iG_().IwV()); }
        @Override public SYo ni_() { return new SYo(100000, super.ni_().IwV()); }

        static final iok ARROW = iok.cfM(205, 205, 205);

        @Override public void Hnt(com.bitwig.graphics.SYo g) {
            super.Hnt(g); // Bitwig's box and text
            try {
                double w = Sxo(), h = yiX(), cx = w - 16, cy = h / 2;
                g.Hnt(ARROW);
                g.MRY(cx - 4.5, cy - 2); g.cfM(cx + 4.5, cy - 2); g.cfM(cx, cy + 3); g.wGc(); // ▾
            } catch (Throwable t) { State.MOD.log("chooser arrow failed", t); }
        }
    }

    /** Empty space that takes whatever width is left (pushes what follows to the right). */
    static final class Stretch extends dve {
        final long h;
        Stretch(long h) { this.h = h; }
        @Override public SYo mK_() { return new SYo(0, h); }
        @Override public SYo iG_() { return new SYo(10, h); }
        @Override public SYo ni_() { return new SYo(100000, h); }
        @Override public dve MJ() { return this; }
    }
}
