package com.cimm.minimal;

import bitwig.obf.LJt; // a text value (what a text field shows and sets)
import bitwig.obf.dBX; // Bitwig's dropdown (Settings' dropdowns are these)
import bitwig.obf.dJH; // drag start event (a press that really started)
import bitwig.obf.dKW; // a popup
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.drX; // a press in progress
import bitwig.obf.dve; // a widget
import bitwig.obf.ecV; // a text field
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button
import bitwig.obf.foz; // a change listener
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size
import com.bitwig.graphics.Hpk; // a font at a size

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The font list that opens from Settings > Mods > Font's dropdowns: a "Fonts" title with a close button, a search
 * field and a scrolling list with every font's name drawn in that font. Clicking a font chooses it.
 */
final class Browser {
    private Browser() {}

    static final double W = 340, HEADER = 46, LIST_H = 460, ROW = 40, PAD = 18, NAME_SIZE = 20;

    static final iok BG = iok.cfM(42, 42, 44);
    static final iok LINE = iok.cfM(62, 62, 66);
    static final iok TEXT = iok.cfM(236, 236, 236);
    static final iok DIM = iok.cfM(150, 150, 154);
    static final iok ORANGE = iok.cfM(239, 141, 57);
    static final iok PICKED_BG = iok.cfM(58, 52, 46);

    // ------------------------------------------------------------------------------------------- which dropdown

    /** One of this mod's Settings dropdowns: the framework's value behind it and its options. */
    record Target(Object value, String id, List<String> options) {
        String current() { return FontSettings.currentLabel(id); }
        void choose(String option) {
            // Through the framework first: it only writes (calling the mod's setter, which stores the choice) and
            // tells the dropdown when the value differs from what the getter returns now, so the store must come after.
            if (value != null) {
                try { java.lang.reflect.Method m = value.getClass().getMethod("setValue", Object.class); m.setAccessible(true); m.invoke(value, option); }
                catch (Throwable t) { Fonts.MOD.log("updating the dropdown failed", t); }
            }
            FontSettings.store(id, option); // the mod's own setting (already stored by the setter when that worked)
        }
    }

    /** The first option of each of this mod's dropdowns: no other dropdown in Bitwig has these. */
    private static final Map<String, String> MARKER = Map.of(
            FontSettings.INTERFACE_FIRST, Fonts.INTERFACE,
            FontSettings.BOLD_FIRST, Fonts.BOLD,
            FontSettings.NUMBERS_FIRST, Fonts.NUMBERS);

    private static int checks;

    /** The dropdown's Settings control when it is one of this mod's, else null. */
    static Target target(dBX dropdown) {
        try {
            Found f = search(dropdown);
            boolean log = checks++ < 6;
            if (f.optionValue == null && f.role == null) {
                if (log) Fonts.MOD.log("dropdown " + dropdown.getClass().getName() + ": not ours (" + f.visited + " objects looked at)");
                return null;
            }
            String id = null; List<String> options = null;
            if (f.optionValue != null) {
                Object control = field(f.optionValue, "c");
                if (control != null) {
                    if (!Fonts.MOD.id().equals(field(control, "modId"))) return null;
                    id = String.valueOf(field(control, "id"));
                    @SuppressWarnings("unchecked") List<String> o = (List<String>) field(control, "options");
                    options = o;
                }
            }
            String role = id != null && id.startsWith("ui_font_") ? id.substring("ui_font_".length()) : f.role;
            if (role == null) return null;
            if (options == null) options = FontSettings.options(role);
            if (log) Fonts.MOD.log("dropdown is the " + role + " font (" + (f.optionValue != null ? "framework value at " + f.path : "found by its options") + ")");
            return new Target(f.optionValue, role, options);
        } catch (Throwable t) {
            if (logged.add("target")) Fonts.MOD.log("can't tell which dropdown this is", t);
            return null;
        }
    }

    /** The Settings framework's value behind one of this mod's dropdowns with this setting id, else null. */
    static Object optionValueOf(dBX dropdown, String id) {
        try {
            Found f = search(dropdown);
            if (f.optionValue == null) return null;
            Object control = field(f.optionValue, "c");
            if (control == null || !Fonts.MOD.id().equals(field(control, "modId"))) return null;
            return id.equals(String.valueOf(field(control, "id"))) ? f.optionValue : null;
        } catch (Throwable t) { return null; }
    }

    private static final Set<String> logged = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static final class Found { Object optionValue; String role; String path; int visited; }

    /**
     * Looks through what the dropdown holds (fields, collections, arrays; not other widgets, so never the rest of the
     * window) for the Settings framework's OptionValue, and for one of this mod's marker options.
     */
    private static Found search(dBX dropdown) {
        Found out = new Found();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        queue.add(new Object[]{dropdown, "dropdown", 0});
        while (!queue.isEmpty() && out.visited < 4000) {
            Object[] item = queue.poll();
            Object o = item[0]; String path = (String) item[1]; int depth = (Integer) item[2];
            if (o == null || !seen.add(o)) continue;
            out.visited++;
            if (o instanceof String s) { if (out.role == null) out.role = MARKER.get(s); continue; }
            Class<?> k = o.getClass();
            if (k.getName().endsWith("Controls$OptionValue")) { out.optionValue = o; out.path = path; return out; }
            if (depth >= 5 || o instanceof Number || o instanceof Boolean || o instanceof Class || o instanceof Thread) continue;
            if (o != dropdown && o instanceof dve) continue; // another widget: its parents lead into the whole window
            if (o instanceof Object[] arr) { for (int i = 0; i < arr.length && i < 2000; i++) queue.add(new Object[]{arr[i], path + "[" + i + "]", depth + 1}); continue; }
            if (k.isArray()) continue;
            if (o instanceof java.util.Collection<?> col) { int i = 0; for (Object e : col) { if (i++ > 2000) break; queue.add(new Object[]{e, path + "[]", depth + 1}); } continue; }
            if (o instanceof java.util.Map<?, ?> map) { for (Object e : map.values()) queue.add(new Object[]{e, path + "{}", depth + 1}); continue; }
            if (k.getName().startsWith("java.")) continue;
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().isPrimitive() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try { f.setAccessible(true); queue.add(new Object[]{f.get(o), path + "." + c.getSimpleName() + "." + f.getName(), depth + 1}); }
                    catch (Throwable ignored) {}
                }
            }
        }
        return out;
    }

    /** The Settings framework's value object a settings widget is bound to (a slider's, a dropdown's...), or null. */
    static Object frameworkValue(Object widget) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        queue.add(new Object[]{widget, 0});
        int visited = 0;
        while (!queue.isEmpty() && visited < 5000) {
            Object[] item = queue.poll();
            Object o = item[0]; int depth = (Integer) item[1];
            if (o == null || !seen.add(o)) continue;
            visited++;
            Class<?> k = o.getClass();
            if (k.getName().startsWith("bml.preferences.internal.Controls$") && k.getName().endsWith("Value")) return o;
            if (depth >= 6 || o instanceof String || o instanceof Number || o instanceof Boolean || o instanceof Class || o instanceof Thread) continue;
            if (o != widget && o instanceof dve) continue;
            // arrays (Bitwig keeps a slider's bindings in them), collections and maps are looked into too
            if (o instanceof Object[] arr) { for (int i = 0; i < arr.length && i < 2000; i++) queue.add(new Object[]{arr[i], depth + 1}); continue; }
            if (k.isArray()) continue;
            if (o instanceof java.util.Collection<?> col) { int i = 0; for (Object e : col) { if (i++ > 500) break; queue.add(new Object[]{e, depth + 1}); } continue; }
            if (o instanceof java.util.Map<?, ?> map) { int i = 0; for (Object e : map.values()) { if (i++ > 500) break; queue.add(new Object[]{e, depth + 1}); } continue; }
            if (k.getName().startsWith("java.")) continue;
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass())
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().isPrimitive() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try { f.setAccessible(true); queue.add(new Object[]{f.get(o), depth + 1}); } catch (Throwable ignored) {}
                }
        }
        return null;
    }

    /** The id of the setting behind a framework value (e.g. "background_tone_hue"), when it is this mod's; else null. */
    static String controlId(Object value) {
        try {
            if (value == null) return null;
            Object control = field(value, "control");
            if (control == null || !Fonts.MOD.id().equals(field(control, "modId"))) return null;
            Object id = field(control, "id");
            return id == null ? null : id.toString();
        } catch (Throwable t) { return null; }
    }

    private static Object field(Object o, String name) throws IllegalAccessException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------- the popup

    /** Puts the font list in the dropdown's popup (instead of Bitwig's plain rows). */
    static void fill(dBX dropdown, dKW popup, Target target) {
        Query query = new Query();
        FontList list = new FontList(target, () -> close(dropdown, popup));
        query.onChange = list::filter;

        dWb column = dWb.UNj(1);
        column.HL(new Header(() -> close(dropdown, popup)));
        dWb searchRow = dWb.IwV(0);
        searchRow.HL(new Gap(PAD - 4, 30));
        ecV field = ecV.Hnt(query);
        searchRow.HL(field);
        searchRow.HL(new Gap(PAD - 4, 30));
        column.HL(searchRow);
        column.HL(new Gap(W, 10));
        column.HL(new Rule());
        column.HL(list);
        popup.hUR(column);
        if (logged.add("opened")) Fonts.MOD.log("font list opened for " + target.id() + " (" + target.options().size() + " options)");
    }

    private static void close(dBX dropdown, dKW popup) {
        try { popup.pYg(); } // Bitwig's own close: takes the popup off its window
        catch (Throwable t) { Fonts.MOD.log("closing the font list failed", t); }
    }

    /** What's typed in the search field. */
    static final class Query implements LJt {
        private final List<foz> listeners = new CopyOnWriteArrayList<>();
        String text = "";
        java.util.function.Consumer<String> onChange;
        @Override public Object getValue() { return text; }
        @Override public Object get() { return text; }
        @Override public boolean isSettable() { return true; }
        @Override public boolean isObservable() { return true; }
        private int logs;
        /** Set by the text field as the user types. The field already shows it, so the field isn't told back
         *  (telling it resets its editing mid-typing). */
        @Override public void setValue(Object v) {
            String next = v == null ? "" : v.toString();
            if (logs++ < 6) Fonts.MOD.log("search '" + next + "'");
            if (next.equals(text)) return;
            text = next;
            if (onChange != null) try { onChange.accept(text); } catch (Throwable t) { Fonts.MOD.log("search failed", t); }
        }
        public void addListener(foz l) { listeners.add(l); }
        public void removeListener(foz l) { listeners.remove(l); }
    }

    /** Fixed-size widget base: paints with a clip, a left press runs click() once it really starts. */
    abstract static class Base extends dve {
        /** Width and height (stretch: may grow wider). Plain names: subclasses fill these in, never Bitwig's methods,
         *  because BML renames Bitwig overrides only in classes that extend Bitwig's widget directly. */
        double w() { return W; }
        abstract double h();
        boolean stretch() { return true; }
        @Override public SYo mK_() { return new SYo((long) w(), (long) h()); }
        @Override public SYo iG_() { return new SYo((long) w(), (long) h()); }
        @Override public SYo ni_() { return new SYo(stretch() ? 100000 : (long) w(), (long) h()); }
        @Override public dve MJ() { return this; }
        @Override public void Hnt(com.bitwig.graphics.SYo g) {
            super.Hnt(g);
            try { g.qoU(); g.MRY(0, 0, Sxo(), yiX()); g.lYc(); paint(g, Sxo(), yiX()); }
            catch (Throwable t) { if (logged.add("paint " + getClass().getSimpleName())) Fonts.MOD.log("paint failed: " + getClass().getSimpleName(), t); }
            finally { try { g.ucQ(); } catch (Throwable ignored) {} }
        }
        abstract void paint(com.bitwig.graphics.SYo g, double w, double h);
        // A left press, in plain-named steps (subclasses fill these in; the Bitwig overrides stay here).
        Runnable click(double x, double y) { return null; }
        /** Whether a left press at (x, y) is ours. */
        boolean wantsPress(double x, double y) { return click(x, y) != null; }
        /** The press really started, at (x, y). Default: click there. */
        void pressStart(double x, double y) { Runnable r = click(x, y); if (r != null) r.run(); }
        /** The pointer moved by (dx, dy) while pressed. */
        void pressDrag(double dx, double dy) {}
        /** The press ended. */
        void pressEnd() {}

        @Override public drX cfM(eoX b, double x, double y, eoW m, int n) { drX d = press(b, x, y); return d != null ? d : super.cfM(b, x, y, m, n); }
        @Override public drX MRY(eoX b, double x, double y, eoW m, int n) { drX d = press(b, x, y); return d != null ? d : super.MRY(b, x, y, m, n); }
        private drX press(eoX b, double x, double y) {
            try {
                if (!b.name().equals("SELECTION")) return null;
                if (!wantsPress(x, y)) return null;
                return new drX() { // Bitwig calls press on hover too: act only when the press really starts
                    @Override public void cfM(dJH e) {
                        super.cfM(e);
                        // where the press really is: the event's position (the arguments above can be a hover's)
                        double[] p = where(e, x, y);
                        try { pressStart(p[0], p[1]); } catch (Throwable t) { Fonts.MOD.log("click failed", t); }
                        jbi();
                    }
                    @Override public void cfM(bitwig.obf.dJM e) {
                        super.cfM(e);
                        try { pressDrag(e.ejd(), e.uWE()); } catch (Throwable t) { Fonts.MOD.log("drag failed", t); }
                        jbi();
                    }
                    @Override public void cfM(bitwig.obf.dJI e) {
                        super.cfM(e);
                        try { pressEnd(); } catch (Throwable t) { Fonts.MOD.log("release failed", t); }
                        jbi();
                    }
                    @Override public boolean jQ_() { return false; }
                    @Override public boolean jB_() { return true; }
                };
            } catch (Throwable t) { return null; }
        }

        /**
         * The press's position in this widget, from the press event's public getters (hbP/Grk and Ucl/MIO: one pair
         * is in the widget, the other in the window). The one nearest where Bitwig first asked (x, y) wins.
         */
        private double[] where(dJH e, double x, double y) {
            double[][] c = new double[4][];
            String[] names = {"hbP/Grk", "Ucl/MIO", "hbP/Grk - window", "Ucl/MIO - window"};
            try { c[0] = new double[]{e.hbP(), e.Grk()}; } catch (Throwable ignored) {}
            try { c[1] = new double[]{e.Ucl(), e.MIO()}; } catch (Throwable ignored) {}
            try {
                com.bitwig.base.geom.OLR o = ihs();
                if (o != null) {
                    if (c[0] != null) c[2] = new double[]{c[0][0] - o.FzB(), c[0][1] - o.hUR()};
                    if (c[1] != null) c[3] = new double[]{c[1][0] - o.FzB(), c[1][1] - o.hUR()};
                }
            } catch (Throwable ignored) {}
            int best = -1; double bestD = Double.MAX_VALUE;
            for (int i = 0; i < c.length; i++) {
                if (c[i] == null) continue;
                double d = Math.hypot(c[i][0] - x, c[i][1] - y);
                if (d < bestD) { bestD = d; best = i; }
            }
            double[] p = best < 0 ? new double[]{x, y} : c[best];
            if (logged.add("press " + getClass().getSimpleName()))
                Fonts.MOD.log("press at " + Math.round(p[0]) + "," + Math.round(p[1]) + " from " + (best < 0 ? "the hover" : names[best])
                        + " (asked at " + Math.round(x) + "," + Math.round(y) + ")");
            return p;
        }
    }

    static final class Gap extends Base {
        final double w, h;
        Gap(double w, double h) { this.w = w; this.h = h; }
        @Override double w() { return w; }
        @Override double h() { return h; }
        @Override boolean stretch() { return false; }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {}
    }

    static final class Rule extends Base {
        @Override double h() { return 1; }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) { g.Hnt(LINE); g.MRY(0, 0, w, 1); g.wGc(); }
    }

    /** "Fonts" and the close button. */
    static final class Header extends Base {
        final Runnable close;
        Header(Runnable close) { this.close = close; }
        @Override double h() { return HEADER; }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            Text.plain(g, "Fonts", PAD, h / 2, 17, TEXT);
            double cx = w - PAD - 6, cy = h / 2, r = 7;
            g.Hnt(TEXT); g.IwV(1.6);
            g.MRY(cx - r, cy - r); g.cfM(cx + r, cy + r); g.hbP();
            g.MRY(cx + r, cy - r); g.cfM(cx - r, cy + r); g.hbP();
        }
        @Override Runnable click(double x, double y) { return x > Sxo() - PAD - 28 ? close : null; }
    }

    // ------------------------------------------------------------------------------------------- the list

    /** The list that is open now (for the wheel), if any. */
    private static java.lang.ref.WeakReference<FontList> open = new java.lang.ref.WeakReference<>(null);

    /**
     * Bitwig's wheel / trackpad scroll, before Bitwig handles it (dpl.cfM(eph)): over the open font list it scrolls the
     * list. Every scroll in Bitwig passes here: it returns at once unless a font list is open.
     */
    static void wheel(bitwig.obf.eph e) {
        FontList list = open.get();
        if (list == null) return;
        try {
            com.bitwig.base.geom.OLR o = list.ihs(); // the list's position in its window
            if (o == null) { open = new java.lang.ref.WeakReference<>(null); return; } // closed
            double lx = e.wGc() - o.FzB(), ly = e.eLY() - o.hUR();
            if (lx < 0 || ly < 0 || lx > list.Sxo() || ly > list.yiX()) return;
            if (!"VERTICAL".equals(e.UNj().name())) return;
            boolean pixels = "PIXELS".equals(e.IwV().name());
            double amount = e.FzB();
            if (logged.add("wheel")) Fonts.MOD.log("wheel over the list: " + amount + (pixels ? " px" : " steps"));
            list.scrollBy(pixels ? amount : amount * ROW); // follows the system's scroll direction (Natural scrolling)
        } catch (Throwable t) {
            if (logged.add("wheel failed")) Fonts.MOD.log("wheel failed", t);
        }
    }

    /**
     * The rows: the dropdown's own choices (Bitwig default...) first, then the fonts, each drawn in itself. The list
     * scrolls itself: wheel / trackpad, dragging the rows, or dragging the scrollbar on the right.
     */
    static final class FontList extends Base {
        static final double BAR = 16; // the scrollbar's grab width at the right edge
        final Target target;
        final Runnable close;
        final Map<String, String> pathOf = new HashMap<>();
        List<String> shown;
        double scroll;
        // the press in progress
        double pressX, pressY, moved;
        boolean onBar;

        FontList(Target target, Runnable close) {
            this.target = target; this.close = close;
            for (Fonts.Font f : Fonts.catalogue()) pathOf.put(f.label(), f.path());
            shown = new ArrayList<>(target.options());
            open = new java.lang.ref.WeakReference<>(this);
            int i = shown.indexOf(target.current()); // start with the current font in view
            if (i > 0) scroll = clamp(i * ROW - LIST_H / 2 + ROW / 2);
        }
        @Override double h() { return LIST_H; }

        double contentH() { return shown.size() * ROW; }
        double maxScroll() { return Math.max(0, contentH() - LIST_H); }
        double clamp(double v) { return Math.max(0, Math.min(maxScroll(), v)); }
        void scrollBy(double d) { scroll = clamp(scroll + d); jbi(); }

        void filter(String q) {
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            List<String> next = new ArrayList<>();
            for (String o : target.options()) if (needle.isEmpty() || o.toLowerCase(Locale.ROOT).contains(needle)) next.add(o);
            shown = next;
            scroll = 0;
            jbi();
        }

        double thumbH(double h) { return Math.max(30, h * h / Math.max(h, contentH())); }

        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            g.Hnt(BG); g.MRY(0, 0, w, h); g.wGc();
            scroll = clamp(scroll);
            String current = target.current();
            int first = Math.max(0, (int) (scroll / ROW)), last = Math.min(shown.size() - 1, (int) ((scroll + h) / ROW));
            boolean more = false;
            if (shown.isEmpty()) Text.standard(g, "No fonts match", PAD + 12, ROW / 2, DIM);
            for (int i = first; i <= last; i++) { // only the rows in view get their font loaded
                String label = shown.get(i);
                double y = i * ROW - scroll, cy = y + ROW / 2;
                boolean picked = label.equals(current);
                if (picked) { g.Hnt(PICKED_BG); g.MRY(0, y, w, ROW); g.wGc(); }
                iok c = picked ? ORANGE : TEXT;
                if (picked) { g.Hnt(ORANGE); g.MRY(PAD - 8, cy - 3, 6, 6); g.wGc(); } // the current font
                String path = pathOf.get(label);
                if (path == null) { Text.standard(g, label, PAD + 12, cy, picked ? ORANGE : DIM); continue; }
                Hpk font = Faces.get(path);
                if (font == null && Faces.pending(path)) more = true;
                if (font != null) Text.inFont(g, font, label, PAD + 12, cy, w - PAD - 12 - BAR - 4, c);
                else Text.standard(g, label, PAD + 12, cy, DIM);
            }
            if (maxScroll() > 0) { // the scrollbar
                double th = thumbH(h), ty = scroll / maxScroll() * (h - th);
                g.Hnt(LINE); g.MRY(w - 9, 4, 5, h - 8); g.wGc();
                g.Hnt(onBar && moved > 0 ? TEXT : DIM); g.MRY(w - 9, ty + 4, 5, Math.max(10, th - 8)); g.wGc();
            }
            if (more) jbi(); // load the rest of the visible rows next frame
        }

        @Override boolean wantsPress(double x, double y) { return true; }
        @Override void pressStart(double x, double y) {
            pressX = x; pressY = y; moved = 0;
            onBar = maxScroll() > 0 && x > Sxo() - BAR;
            if (onBar) { // a press on the bar's track (off the thumb) jumps there
                double h = yiX(), th = thumbH(h), ty = scroll / Math.max(1, maxScroll()) * (h - th);
                if (y < ty || y > ty + th) scroll = clamp((y - th / 2) / Math.max(1, h - th) * maxScroll());
            }
        }
        @Override void pressDrag(double dx, double dy) {
            moved += Math.abs(dy) + Math.abs(dx);
            if (onBar) { double h = yiX(); scrollBy(dy * maxScroll() / Math.max(1, h - thumbH(h))); }
            else if (moved > 4) scrollBy(-dy); // drag the rows
        }
        @Override void pressEnd() {
            if (onBar || moved > 4) { moved = 0; onBar = false; return; }
            int i = (int) ((pressY + scroll) / ROW);
            if (i < 0 || i >= shown.size()) return;
            String label = shown.get(i);
            target.choose(label);
            Fonts.MOD.log(target.id() + " -> " + label + " (restart Bitwig to apply)");
            close.run();
        }
    }

    // ------------------------------------------------------------------------------------------- fonts and text

    /** Font faces for the list, made from the font files with Bitwig's own graphics system, a few per frame. */
    static final class Faces {
        private static final Map<String, Hpk> cache = new HashMap<>();
        private static final Set<String> failed = new HashSet<>();
        private static int loadedThisFrame;
        private static long frame;
        static boolean pending(String path) { return !failed.contains(path) && !cache.containsKey(path); }
        static Hpk get(String path) {
            Hpk h = cache.get(path);
            if (h != null || failed.contains(path)) return h;
            long now = System.nanoTime() / 16_000_000L;
            if (now != frame) { frame = now; loadedThisFrame = 0; }
            if (loadedThisFrame >= 6) return null;
            loadedThisFrame++;
            com.bitwig.graphics.sal gs = Minimal.graphics;
            if (gs == null) { failed.add(path); return null; }
            try {
                h = gs.cfM(gs.cfM(new File(path)), NAME_SIZE);
                cache.put(path, h);
            } catch (Throwable t) {
                failed.add(path);
                if (logged.add("face")) Fonts.MOD.log("couldn't load " + path + " for the list", t);
            }
            return h;
        }
    }

    static final class Text {
        private static double defaultH = Double.NaN;
        /** Bitwig's UI font at roughly the given size, left at x, centred on cy. */
        static void plain(com.bitwig.graphics.SYo g, String s, double x, double cy, double size, iok c) {
            if (Double.isNaN(defaultH)) {
                try { defaultH = g.iHb().FzB(); } catch (Throwable t) { defaultH = 15; }
                if (!(defaultH > 4)) defaultH = 15;
            }
            double k = size * 1.2 / defaultH;
            g.qoU();
            try { g.FzB(x, cy + size * 0.36); g.Hnt(k, k); g.cfM(0, 0, s, c); } finally { g.ucQ(); }
        }
        /** Bitwig's UI font at its standard size (as the rest of the interface), left at x, centred on cy. */
        static void standard(com.bitwig.graphics.SYo g, String s, double x, double cy, iok c) {
            double h = uiHeight(g);
            g.qoU();
            try { g.cfM(x, cy + h * 0.3, s, c); } finally { g.ucQ(); }
        }
        /** The standard UI font's height. */
        static double uiHeight(com.bitwig.graphics.SYo g) {
            if (Double.isNaN(defaultH)) {
                try { defaultH = g.iHb().FzB(); } catch (Throwable t) { defaultH = 15; }
                if (!(defaultH > 4)) defaultH = 15;
            }
            return defaultH;
        }
        /** s in its own font, cut to fit maxW. */
        static void inFont(com.bitwig.graphics.SYo g, Hpk font, String s, double x, double cy, double maxW, iok c) {
            double ui = uiHeight(g);
            g.qoU();
            try {
                g.cfM(font);
                double fh = NAME_SIZE;
                try { double m = g.iHb().FzB(); if (m > 4) fh = m; } catch (Throwable ignored) {}
                double k = ui / fh; // as tall as the standard UI text
                g.FzB(x, cy + ui * 0.3);
                g.Hnt(k, k);
                double room = maxW / k;
                if (width(g, s) > room) {
                    while (s.length() > 1 && width(g, s + "…") > room) s = s.substring(0, s.length() - 1);
                    s = s + "…";
                }
                g.cfM(0, 0, s, c);
            } finally { g.ucQ(); }
        }
        private static double width(com.bitwig.graphics.SYo g, String s) {
            try { return g.cfM(s).IwV(); } catch (Throwable t) { return s.length() * NAME_SIZE * 0.55; }
        }
    }
}
