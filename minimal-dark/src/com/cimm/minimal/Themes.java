package com.cimm.minimal;

import bitwig.obf.AQw; // a boolean value a native button shows and sets
import bitwig.obf.LJt; // a text value (a label shows it, a text field sets it)
import bitwig.obf.dAd; // Bitwig's button
import bitwig.obf.dBX; // Bitwig's dropdown
import bitwig.obf.dKW; // a popup
import bitwig.obf.dWb; // a row or column of widgets
import bitwig.obf.dve; // a widget
import bitwig.obf.dzg; // button base: x3t() content, TMF the standard style
import bitwig.obf.ecV; // a text field
import bitwig.obf.foz; // a change listener
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User Themes: the Look dropdown (Settings > Mods > Minimal Dark) opens a popup with two tabs, Presets and User
 * Themes, in Bitwig's own option buttons (as the Groove tool's tabs). Below the list, a name field and Save keep the
 * current look (Background sliders and highlight colour) as a theme of the user's own.
 *
 * Saved themes are kept in the mod's settings (user_themes), so they are there again after Bitwig restarts. Choosing
 * one makes it the Custom look (its values become the Custom values) and the dropdown shows its name; touching a
 * slider afterwards turns the look back into plain Custom (Save again with the same name to update the theme).
 */
final class Themes {
    private Themes() {}

    static final String KEY = "user_themes", ACTIVE = "user_look";
    static final int PRESETS = 0, USER = 1;
    /** The open tab, remembered while Bitwig runs. */
    static volatile int tab = -1;

    // ------------------------------------------------------------------------------------------- the saved themes

    /** The saved themes, in the order they were first saved. */
    static synchronized List<Looks.Look> user() {
        List<Looks.Look> out = new ArrayList<>();
        String s = Minimal.STORE.get(KEY, "");
        if (s == null || s.isEmpty()) return out;
        for (String line : s.split(";")) {
            String[] f = line.split(",");
            if (f.length != 7) continue;
            try {
                out.add(new Looks.Look(URLDecoder.decode(f[0], StandardCharsets.UTF_8), Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6])));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static synchronized void store(List<Looks.Look> themes) {
        StringBuilder b = new StringBuilder();
        for (Looks.Look l : themes) {
            if (b.length() > 0) b.append(';');
            b.append(URLEncoder.encode(l.name(), StandardCharsets.UTF_8)).append(',').append(l.hue()).append(',').append(l.sat())
             .append(',').append(l.lum()).append(',').append(l.con()).append(',').append(l.highlight()).append(',').append(l.customHue());
        }
        Minimal.STORE.put(KEY, b.toString());
        try { Minimal.STORE.flush(); } catch (Throwable t) { Minimal.MOD.log("writing the themes failed", t); }
    }

    /** The saved theme in use now ("" when the look is a preset or plain Custom). */
    static String active() {
        if (!Looks.custom()) return "";
        String a = Minimal.STORE.get(ACTIVE, "");
        return a == null ? "" : a;
    }

    static void clearActive() {
        if (Minimal.STORE.get(ACTIVE, "").isEmpty()) return;
        Minimal.STORE.remove(ACTIVE);
        NAME.fire();
    }

    /** The current look kept under this name (a theme of the same name is replaced, in its place). */
    static String save(String typed) {
        String name = clean(typed);
        List<Looks.Look> themes = user();
        if (name.isEmpty()) {
            int n = themes.size() + 1;
            while (find(themes, "My Theme " + n) >= 0) n++;
            name = "My Theme " + n;
        }
        Looks.Look l = new Looks.Look(name, Tone.hue, Tone.sat, Tone.lum, Tone.con, Minimal.choice(), Minimal.hue());
        int at = find(themes, name);
        if (at >= 0) themes.set(at, l); else themes.add(l);
        store(themes);
        Looks.useSaved(l); // it is the look in use now
        Minimal.MOD.log("theme saved: " + name + " (" + themes.size() + " saved)");
        return name;
    }

    static void delete(String name) {
        List<Looks.Look> themes = user();
        int at = find(themes, name);
        if (at < 0) return;
        themes.remove(at);
        store(themes);
        if (name.equals(active())) clearActive(); // the values stay, as plain Custom
        Minimal.MOD.log("theme deleted: " + name);
    }

    private static int find(List<Looks.Look> themes, String name) {
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).name().equalsIgnoreCase(name)) return i;
        return -1;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > 40 ? s.substring(0, 40).trim() : s;
    }

    /** What the Look dropdown shows: the saved theme's name, else the option's own. */
    static String displayName() {
        String a = active();
        if (!a.isEmpty()) return a;
        String[] labels = Looks.labels();
        int i = Looks.current();
        return i >= 0 && i < labels.length ? labels[i] : Looks.CUSTOM;
    }

    // ------------------------------------------------------------------------------------------- the dropdown

    /** Small observable values for Bitwig's widgets (labels and buttons). */
    abstract static class Value {
        private final List<foz> listeners = new CopyOnWriteArrayList<>();
        private Object last;
        public boolean isObservable() { return true; }
        public boolean isSettable() { return true; }
        public void addListener(foz l) { listeners.add(l); }
        public void removeListener(foz l) { listeners.remove(l); }
        abstract Object value();
        /** Tells the bound widgets the value changed. */
        void fire() {
            Object v = value(), old = last;
            last = v;
            if (listeners.isEmpty()) return;
            bitwig.obf.VGL e = new bitwig.obf.VGL((bitwig.obf.rMe) this, old, v);
            for (foz l : listeners) try { l.eventOccurred(e); } catch (Throwable t) { Minimal.MOD.log("updating a widget failed", t); }
        }
    }

    /** The name the Look dropdown shows (a saved theme's name included, which isn't one of its options). */
    static final class Name extends Value implements LJt {
        @Override Object value() { return displayName(); }
        @Override public Object getValue() { return displayName(); }
        @Override public Object get() { return displayName(); }
        @Override public void setValue(Object v) {}
        @Override public boolean isSettable() { return false; }
    }
    static final Name NAME = new Name();

    /** What's typed in the theme name field. */
    static final class Typed extends Value implements LJt {
        String text = "";
        @Override Object value() { return text; }
        @Override public Object getValue() { return text; }
        @Override public Object get() { return text; }
        @Override public void setValue(Object v) { text = v == null ? "" : v.toString(); } // the field shows it already
    }

    /** A tab: lit while it is open; pressing opens it. */
    static final class Tab extends Value implements AQw {
        final int index; final Runnable opened;
        Tab(int index, Runnable opened) { this.index = index; this.opened = opened; }
        @Override Object value() { return tab == index; }
        @Override public Object getValue() { return tab == index; }
        @Override public boolean getBoolValue() { return tab == index; }
        @Override public void setBoolValue(boolean b) { open(); }
        @Override public void setValue(Object v) { open(); }
        private void open() {
            tab = index;
            try { opened.run(); } catch (Throwable t) { Minimal.MOD.log("opening the tab failed", t); }
        }
    }

    /** Bitwig's standard text button at a fixed width (as the Groove tool's tabs and buttons). */
    static final class Button extends dAd {
        final long width;
        Button(String text, long width) {
            super();
            this.width = width;
            Hnt(dzg.x3t(), dzg.TMF);
            G_();
            IwV(text);
        }
        @Override public SYo mK_() { return new SYo(width, super.mK_().IwV()); }
        @Override public SYo iG_() { return new SYo(width, super.iG_().IwV()); }
        @Override public SYo ni_() { return new SYo(width, super.ni_().IwV()); }
    }

    private static final Map<Object, Object> lookValues = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object NOT_LOOK = new Object();
    private static Set<String> lookLabels;

    /** The Settings framework's value behind the Look dropdown, or null when the dropdown is another one. */
    static Object lookValue(dBX dropdown) {
        Object v = lookValues.get(dropdown);
        if (v == null) {
            v = Browser.optionValueOf(dropdown, Looks.LOOK_ID);
            lookValues.put(dropdown, v == null ? NOT_LOOK : v);
        }
        return v == NOT_LOOK ? null : v;
    }

    /** The dropdown's label value: for the Look dropdown, its name (a saved theme's included). */
    static LJt label(dBX dropdown, Object shown, LJt value) {
        try {
            if (lookLabels == null) lookLabels = Set.of(Looks.labels());
            if (!(shown instanceof String s) || !lookLabels.contains(s)) return value; // quick: not the Look dropdown
            if (lookValue(dropdown) == null) return value;
            NAME.fire();
            return NAME;
        } catch (Throwable t) { return value; }
    }

    /** Fills the Look dropdown's popup (tabs, list, save); false when the dropdown is another one. */
    static boolean fill(dBX dropdown, dKW popup) {
        Object value = lookValue(dropdown);
        if (value == null) return false;
        Looks.dropdownValue = value;
        Tone.anchor(dropdown); // the Settings page: its sliders are found from here
        if (tab < 0) tab = active().isEmpty() ? PRESETS : USER;
        Runnable close = () -> { try { popup.pYg(); } catch (Throwable t) { Minimal.MOD.log("closing the themes failed", t); } };
        ThemeList list = new ThemeList(value, close);
        Tab[] tabs = new Tab[2];
        Runnable opened = () -> { for (Tab t : tabs) t.fire(); list.scroll = 0; list.jbi(); };
        tabs[0] = new Tab(PRESETS, opened);
        tabs[1] = new Tab(USER, opened);

        double inner = Browser.W - 2 * Browser.PAD;
        dWb tabRow = dWb.IwV(0);
        tabRow.HL(new Browser.Gap(Browser.PAD, 30));
        String[] names = {"Presets", "User Themes"};
        for (int i = 0; i < 2; i++) {
            if (i > 0) tabRow.Hnt(2);
            Button b = new Button(names[i], (long) ((inner - 2) / 2));
            try { b.MRY(b.LIK(), tabs[i]); } catch (Throwable t) { Minimal.MOD.log("tab binding failed", t); }
            tabRow.HL(b);
        }
        tabRow.HL(new Browser.Gap(Browser.PAD, 30));

        Typed typed = new Typed();
        dWb saveRow = dWb.IwV(0);
        saveRow.HL(new Browser.Gap(Browser.PAD, 30));
        saveRow.HL(ecV.Hnt(typed));
        saveRow.HL(new Browser.Gap(8, 30));
        Button save = new Button("Save", 76);
        save.Hnt((Runnable) () -> {
            try {
                String name = save(typed.text);
                tab = USER;
                list.scrollToEnd = name;
                opened.run();
            } catch (Throwable t) { Minimal.MOD.log("saving the theme failed", t); }
        });
        saveRow.HL(save);
        saveRow.HL(new Browser.Gap(Browser.PAD, 30));

        dWb column = dWb.UNj(1);
        column.HL(new Browser.Gap(Browser.W, 12));
        column.HL(tabRow);
        column.HL(new Browser.Gap(Browser.W, 10));
        column.HL(new Browser.Rule());
        column.HL(list);
        column.HL(new Browser.Rule());
        column.HL(new Caption("Save the current look as a theme:"));
        column.HL(saveRow);
        column.HL(new Browser.Gap(Browser.W, 14));
        popup.hUR(column);
        open = new java.lang.ref.WeakReference<>(list);
        Minimal.MOD.log("themes opened on " + (tab == USER ? "User Themes" : "Presets") + " (" + user().size() + " saved)");
        return true;
    }

    /** A line of small dim text. */
    static final class Caption extends Browser.Base {
        final String text;
        Caption(String text) { this.text = text; }
        @Override double h() { return 30; }
        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) { Browser.Text.standard(g, text, Browser.PAD, h / 2 + 2, Browser.DIM); }
    }

    // ------------------------------------------------------------------------------------------- the list

    private static java.lang.ref.WeakReference<ThemeList> open = new java.lang.ref.WeakReference<>(null);

    /** Wheel / trackpad over the open themes list: scrolls it. True when it was over the list. */
    static boolean wheel(bitwig.obf.eph e) {
        ThemeList list = open.get();
        if (list == null) return false;
        try {
            com.bitwig.base.geom.OLR o = list.ihs();
            if (o == null) { open = new java.lang.ref.WeakReference<>(null); return false; }
            double lx = e.wGc() - o.FzB(), ly = e.eLY() - o.hUR();
            if (lx < 0 || ly < 0 || lx > list.Sxo() || ly > list.yiX()) return false;
            if (!"VERTICAL".equals(e.UNj().name())) return false;
            boolean pixels = "PIXELS".equals(e.IwV().name());
            list.scrollBy(pixels ? e.FzB() : e.FzB() * ThemeList.ROW);
            return true;
        } catch (Throwable t) { return false; }
    }

    /** The open tab's looks: Custom and the presets, or the saved themes (each with × to delete it). */
    static final class ThemeList extends Browser.Base {
        static final double ROW = 32, ROWS = 11, BAR = 16;
        final Object value; final Runnable close;
        double scroll, pressX, pressY, moved;
        String scrollToEnd;

        ThemeList(Object value, Runnable close) { this.value = value; this.close = close; }
        @Override double h() { return ROW * ROWS; }

        /** The rows' names and which one is in use. */
        List<String> rows() {
            List<String> out = new ArrayList<>();
            if (tab == USER) for (Looks.Look l : user()) out.add(l.name());
            else out.addAll(List.of(Looks.labels()));
            return out;
        }
        int picked(List<String> rows) {
            if (tab == USER) return rows.indexOf(active());
            return active().isEmpty() ? Looks.current() : -1;
        }

        double maxScroll(int n) { return Math.max(0, n * ROW - h()); }
        void scrollBy(double d) { scroll = Math.max(0, Math.min(maxScroll(rows().size()), scroll + d)); jbi(); }

        private iok accent() {
            try { iok c = PEn().cfM("Accent (default)").cfM(this); if (c != null) return c; } catch (Throwable ignored) {}
            return Browser.ORANGE;
        }

        @Override void paint(com.bitwig.graphics.SYo g, double w, double h) {
            List<String> rows = rows();
            if (scrollToEnd != null) { // just saved: that theme in view
                int i = rows.indexOf(scrollToEnd);
                if (i >= 0) scroll = Math.max(0, Math.min(maxScroll(rows.size()), i * ROW - h / 2 + ROW / 2));
                scrollToEnd = null;
            }
            scroll = Math.max(0, Math.min(maxScroll(rows.size()), scroll));
            iok text = Theme.colour("Default text");
            if (text == null) text = Browser.TEXT;
            iok accent = accent();
            int picked = picked(rows);
            if (rows.isEmpty()) {
                Browser.Text.standard(g, "No saved themes yet.", Browser.PAD + 12, ROW, Browser.DIM);
                Browser.Text.standard(g, "Name the current look and click Save.", Browser.PAD + 12, ROW * 2, Browser.DIM);
                return;
            }
            int first = Math.max(0, (int) (scroll / ROW)), last = Math.min(rows.size() - 1, (int) ((scroll + h) / ROW));
            for (int i = first; i <= last; i++) {
                double y = i * ROW - scroll, cy = y + ROW / 2;
                boolean on = i == picked;
                if (on) {
                    g.Hnt(new iok(accent.FzB(), accent.IwV(), accent.UNj(), 0.16f));
                    g.MRY(6, y + 2, w - 12 - (maxScroll(rows.size()) > 0 ? BAR - 4 : 0), ROW - 4, (ROW - 4) / 2, Minimal.ALL_CORNERS);
                    g.wGc();
                    g.Hnt(accent); g.MRY(Browser.PAD - 6, cy - 3, 6, 6); g.wGc(); // the look in use
                }
                Browser.Text.standard(g, rows.get(i), Browser.PAD + 10, cy, on ? accent : text);
                if (tab == USER) { // × deletes
                    double cx = deleteX(w), r = 4.5;
                    g.Hnt(Browser.DIM); g.IwV(1.4);
                    g.MRY(cx - r, cy - r); g.cfM(cx + r, cy + r); g.hbP();
                    g.MRY(cx + r, cy - r); g.cfM(cx - r, cy + r); g.hbP();
                }
            }
            double max = maxScroll(rows.size());
            if (max > 0) {
                double th = Math.max(30, h * h / (rows.size() * ROW)), ty = scroll / max * (h - th);
                g.Hnt(Browser.LINE); g.MRY(w - 9, 4, 5, h - 8); g.wGc();
                g.Hnt(Browser.DIM); g.MRY(w - 9, ty + 4, 5, Math.max(10, th - 8)); g.wGc();
            }
        }

        double deleteX(double w) { return w - Browser.PAD - 6 - (maxScroll(rows().size()) > 0 ? BAR - 4 : 0); }

        @Override boolean wantsPress(double x, double y) { return true; }
        @Override void pressStart(double x, double y) { pressX = x; pressY = y; moved = 0; }
        @Override void pressDrag(double dx, double dy) { moved += Math.abs(dx) + Math.abs(dy); if (moved > 4) scrollBy(-dy); }
        @Override void pressEnd() {
            if (moved > 4) { moved = 0; return; }
            List<String> rows = rows();
            int i = (int) ((pressY + scroll) / ROW);
            if (i < 0 || i >= rows.size()) return;
            String name = rows.get(i);
            if (tab == USER) {
                if (Math.abs(pressX - deleteX(Sxo())) < 14) { delete(name); jbi(); return; }
                for (Looks.Look l : user()) if (l.name().equals(name)) { Looks.useSaved(l); break; }
            } else {
                Looks.usePreset(value, i);
            }
            close.run();
        }
    }
}
