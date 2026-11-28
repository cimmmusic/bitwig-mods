package com.cimm.minimal;

import bitwig.obf.iok;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background tone and text contrast.
 *
 * Every theme colour passes through Minimal.resolved as Bitwig draws it. Colours are sorted once by name:
 * - background (panels, fields, strokes: any name that isn't text, an icon, a meter, the track default or the accent):
 *   when its value is a neutral grey it gets the user's Background settings (contrast around the theme's dark level,
 *   then luminance, then saturation towards the chosen hue);
 * - foreground (names with text, icon, label...): when the toned background turns light, neutral foregrounds flip
 *   their lightness (white text becomes black), keeping their transparency;
 * - kept as they are: coloured values, "Dark Text" / "Subtle Dark Text" / "Lighter Text" (the text Bitwig puts on
 *   coloured items, chosen per item by contrast, see Minimal.brightness), meters, track colours.
 */
final class Tone {
    private Tone() {}

    static volatile int hue = Minimal.STORE.getInt("bg_hue", 270);
    static volatile int sat = Minimal.STORE.getInt("bg_saturation", 0);
    static volatile int lum = Minimal.STORE.getInt("bg_luminance", 0);
    static volatile int con = Minimal.STORE.getInt("bg_contrast", 0);

    static final byte KEEP = 0, BG = 1, FG = 2;
    private static final Map<Object, Byte> kinds = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> CONTEXT = Set.of("Lighter Text", Minimal.INK);
    private static final java.util.regex.Pattern FOREGROUND =
            java.util.regex.Pattern.compile("(?i).*(text|icon|label|knob line|hitcount|cursor|playhead|caret).*");
    private static volatile Object windowColour;
    private static volatile iok windowSeen;

    /** Every named theme colour, as Bitwig registers it. */
    static void register(Object colour, String name) {
        byte k;
        if (CONTEXT.contains(name) || name.equals("track") || name.contains("Meter") || Minimal.NAMES.contains(name)) k = KEEP;
        else if (FOREGROUND.matcher(name).matches()) k = FG;
        else k = BG;
        kinds.put(colour, k);
        if (name.equals("Window background")) windowColour = colour;
    }

    /**
     * Bitwig keeps colours it has resolved on its widgets and resolves them again only when a theme colour changes.
     * After a tone change every background and foreground colour is set to a hair-different value and straight back
     * (efb.Hnt), which is Bitwig's own "this colour changed" signal: everything is drawn again with the new tone.
     * Call on the UI thread, from a setting changing (not while painting).
     */
    static void refreshAll() {
        Object[] cs;
        synchronized (kinds) { cs = kinds.entrySet().stream().filter(e -> e.getValue() != KEEP).map(Map.Entry::getKey).toArray(); }
        int n = 0;
        for (Object o : cs) {
            if (!(o instanceof bitwig.obf.efb c)) continue;
            try {
                iok v = c.iHb();
                if (v == null) continue;
                float a = v.hUR();
                c.Hnt(new iok(v.FzB(), v.IwV(), v.UNj(), a > 0.5f ? a - 0.002f : a + 0.002f));
                c.Hnt(v);
                n++;
            } catch (Throwable ignored) {}
        }
        if (refreshLogs++ < 3) Minimal.MOD.log("tone refresh: " + n + " colours");
    }
    private static int refreshLogs;
    private static volatile long refreshAt;
    private static java.util.TimerTask refreshTask;

    /** refreshAll() once the setting stops moving (a slider drag sends many changes): ~150 ms after the last one. */
    static synchronized void refreshSoon() {
        refreshAt = System.nanoTime() + 150_000_000L;
        if (refreshTask != null) return;
        try {
            refreshTask = bitwig.obf.dpN.TVZ().cfM(() -> {
                if (System.nanoTime() < refreshAt) return;
                synchronized (Tone.class) { if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; } }
                refreshAll();
                repaint();
            }, 50, 50);
        } catch (Throwable t) { refreshTask = null; refreshAll(); }
    }

    static boolean active() { return sat != 0 || lum != 0 || con != 0; }

    /** The window background as drawn (with the tone applied), or null before Bitwig has drawn a window. */
    static iok windowBackground() {
        iok w = windowSeen;
        if (w == null) return null;
        Object c = windowColour;
        return c == null ? w : adjust(c, w);
    }
    private static volatile boolean light;
    static boolean isLight() { return light; }
    private static final ConcurrentHashMap<Long, iok> cache = new ConcurrentHashMap<>();
    private static final iok SAME = new iok(0f, 0f, 0f, 0f);

    /** Settings changed: forget the cached results and work out again whether the background is now light. */
    static void changed() {
        cache.clear();
        Legible.clear();
        iok w = windowSeen;
        light = active() && w != null && luminance(background(w)) > 0.4;
        Minimal.retarget(); // the highlight stays readable against the new background
        Minimal.MOD.log("background tone: hue " + hue + "° saturation " + sat + "% luminance " + lum + "% contrast " + con
                + "%" + (light ? " (light: dark text)" : ""));
    }

    /** The colour to draw instead of value for this theme colour. */
    static iok adjust(Object colour, iok value) {
        if (colour == windowColour && windowSeen == null) { windowSeen = value; changed(); }
        if (!active()) return value;
        Byte k = kinds.get(colour);
        if (k == null || k == KEEP) return value;
        long key = ((long) System.identityHashCode(colour) << 32) ^ (pack(value) & 0xFFFFFFFFL);
        iok out = cache.get(key);
        if (out == null) {
            iok r = null;
            if (neutral(value)) r = k == BG ? background(value) : light ? invert(value) : null;
            out = r == null ? SAME : r;
            if (cache.size() < 8192) cache.put(key, out);
        }
        return out == SAME ? value : out;
    }

    // ------------------------------------------------------------------------------------------- the maths

    static boolean neutral(iok c) {
        float max = Math.max(c.FzB(), Math.max(c.IwV(), c.UNj())), min = Math.min(c.FzB(), Math.min(c.IwV(), c.UNj()));
        return max - min < 0.04f || (max > 0 && (max - min) / max < 0.15f);
    }

    /** A background grey toned: contrast around the dark level, luminance, then saturation towards the hue. */
    static iok background(iok c) {
        float[] hsl = hsl(c);
        double l = hsl[2], pivot = 0.1;
        l = pivot + (l - pivot) * (1 + con / 100.0);
        l = Math.max(0, Math.min(1, l));
        double d = lum / 100.0;
        l = d >= 0 ? l + (1 - l) * d : l * (1 + d);
        double s = Math.max(0, Math.min(1, hsl[1] + sat / 100.0));
        return rgb(hue / 360.0, s, Math.max(0, Math.min(1, l)), c.hUR());
    }

    /** A neutral foreground with its lightness flipped (white text on a light background becomes black). */
    static iok invert(iok c) {
        float[] hsl = hsl(c);
        return rgb(hsl[0], hsl[1], 1 - hsl[2], c.hUR());
    }

    /** WCAG relative luminance. */
    static double luminance(iok c) {
        return 0.2126 * lin(c.FzB()) + 0.7152 * lin(c.IwV()) + 0.0722 * lin(c.UNj());
    }
    private static double lin(float v) { return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    private static float[] hsl(iok c) {
        float r = c.FzB(), g = c.IwV(), b = c.UNj();
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
        }
        return new float[]{h, s, l};
    }

    private static iok rgb(double h, double s, double l, float a) {
        if (s <= 0) return new iok((float) l, (float) l, (float) l, a);
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new iok((float) channel(p, q, h + 1 / 3.0), (float) channel(p, q, h), (float) channel(p, q, h - 1 / 3.0), a);
    }
    private static double channel(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1 / 2.0) return q;
        if (t < 2 / 3.0) return p + (q - p) * (2 / 3.0 - t) * 6;
        return p;
    }

    private static int pack(iok c) {
        return (Math.round(c.hUR() * 255) << 24) | (Math.round(c.FzB() * 255) << 16) | (Math.round(c.IwV() * 255) << 8) | Math.round(c.UNj() * 255);
    }

    // ------------------------------------------------------------------------------------------- repainting

    /** Bitwig's windows, found from the widgets that ask for colours (to repaint them all when a setting changes). */
    static final Set<Object> windows = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static int asked;

    static void noteWidget(Object widget) {
        if ((asked++ & 511) != 0 || !(widget instanceof bitwig.obf.dve w)) return;
        try {
            bitwig.obf.dve root = w;
            for (bitwig.obf.dve p = w.RbC(); p != null; p = p.RbC()) root = p;
            windows.add(root);
        } catch (Throwable ignored) {}
    }

    static void repaint() {
        Object[] ws;
        synchronized (windows) { ws = windows.toArray(); }
        for (Object w : ws) try { ((bitwig.obf.dve) w).jbi(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------------------------------- reset

    /** The Background settings' ids (as in HighlightSettings) and their defaults: the default dark look. */
    static final Map<String, Integer> DEFAULTS = Map.of("background_tone_hue", 270, "background_tone_saturation", 0,
            "background_tone_luminance", 0, "background_tone_contrast", 0);

    /**
     * A double-click on a settings slider: if it is one of the Background sliders (found by the setting it is bound to,
     * through the Settings framework), all four go back to the default dark look: the settings directly, and every
     * Background slider in the window through its framework value (so the sliders move too).
     */
    static void resetFrom(bitwig.obf.dNg slider) {
        String id = Browser.controlId(Browser.frameworkValue(slider));
        if (id == null || !DEFAULTS.containsKey(id)) return; // not one of the Background sliders
        long now = System.nanoTime();
        if (now - lastReset < 500_000_000L) return; // one reset per double-click
        lastReset = now;
        anchor(slider);
        bitwig.obf.dve root = root(slider);
        java.util.List<bitwig.obf.dNg> sliders = new java.util.ArrayList<>();
        collect(root, sliders);
        int n = 0;
        for (bitwig.obf.dNg s : sliders) {
            Object v = Browser.frameworkValue(s);
            String sid = Browser.controlId(v);
            Integer def = sid == null ? null : DEFAULTS.get(sid);
            if (def == null || !(v instanceof bitwig.obf.rMe value)) continue;
            try { value.setValue(Double.valueOf(def)); n++; } catch (Throwable t) { Minimal.MOD.log("slider reset failed", t); }
        }
        // stored after the sliders moved (the framework moves a slider only when its value differs from the stored one)
        hue = 270; sat = 0; lum = 0; con = 0;
        Minimal.STORE.putInt("bg_hue", 270); Minimal.STORE.putInt("bg_saturation", 0);
        Minimal.STORE.putInt("bg_luminance", 0); Minimal.STORE.putInt("bg_contrast", 0);
        changed();
        refreshAll();
        repaint();
        Minimal.MOD.log("background back to default (" + n + " sliders moved)");
    }

    private static volatile long lastReset;

    /**
     * Shows new values on any open Settings page: every slider and dropdown of this mod whose setting id is in values
     * is set through its Settings-framework value (a number for sliders, the option's label for dropdowns), so the
     * control moves and its setting is stored. Returns how many controls were set.
     */
    /** Settings sliders and dropdowns, noted as they draw (weakly held). */
    private static final Set<Object> controlsSeen = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Each noted control's setting id ("" when it isn't one of this mod's). */
    private static final Map<Object, String> controlIds = Collections.synchronizedMap(new WeakHashMap<>());

    /** A control known to be on the Settings page (the Look dropdown, once opened): its window holds the others. */
    static volatile java.lang.ref.WeakReference<bitwig.obf.dve> anchor = new java.lang.ref.WeakReference<>(null);

    static void anchor(bitwig.obf.dve w) { if (w != null) anchor = new java.lang.ref.WeakReference<>(w); }

    private static bitwig.obf.dve root(bitwig.obf.dve w) {
        bitwig.obf.dve r = w;
        for (int i = 0; i < 400 && r != null && r.RbC() != null; i++) r = r.RbC();
        return r;
    }

    static void noteControl(Object w) {
        if (controlsSeen.size() >= 2000 || !controlsSeen.add(w)) return;
        if (!(w instanceof bitwig.obf.dve d)) return;
        boolean inSettings = false; // only the Settings page's controls are looked into; others: skipped at once
        for (bitwig.obf.dve p = d; p != null; p = p.RbC())
            if (p instanceof bitwig.obf.aBl) { inSettings = true; break; }
        bitwig.obf.dve a = anchor.get();
        if (!inSettings && a != null && root(a) == root(d)) inSettings = true;
        if (!inSettings) return; // not cached: it may turn out to be on the page (settingsControls looks again)
        String id = id(d);
        if (id != null && !id.isEmpty()) {
            if (anchor.get() == null) anchor(d);
            if (Looks.CUSTOM_IDS.contains(id)) dim(w, !Looks.custom());
        }
    }

    /** The setting id behind a control ("" when it isn't one of this mod's), worked out once per control. */
    private static String id(bitwig.obf.dve c) {
        String id = controlIds.get(c);
        if (id == null) {
            id = Browser.controlId(Browser.frameworkValue(c));
            if (id == null) id = "";
            controlIds.put(c, id);
        }
        return id;
    }

    /** Every slider and dropdown in the Settings window (found from the anchor, through the widget tree). */
    static java.util.List<bitwig.obf.dve> settingsControls() {
        java.util.List<bitwig.obf.dve> out = new java.util.ArrayList<>();
        bitwig.obf.dve a = anchor.get();
        if (a != null) controls(root(a), out, 0);
        return out;
    }

    /** Dims (or undims) the Custom-only controls' rows: they only apply while the Look is Custom. */
    static void dimControls(boolean custom) {
        for (bitwig.obf.dve c : settingsControls()) {
            String id = id(c);
            if (Looks.CUSTOM_IDS.contains(id)) dim(c, !custom);
        }
    }

    private static void dim(Object control, boolean dimmed) {
        if (!(control instanceof bitwig.obf.dve w)) return;
        try {
            bitwig.obf.dve row = w.RbC() != null ? w.RbC() : w; // the row: the label and the control
            row.Zyp(dimmed ? 0.35 : 1.0);
        } catch (Throwable t) { Minimal.MOD.log("dimming a setting failed", t); }
    }
    static String kindOf(Object colour) { Byte k = kinds.get(colour); return k == null ? "unknown" : k == BG ? "background" : k == FG ? "foreground" : "kept"; }

    static int showOnSettingsPage(Map<String, Object> values) {
        java.util.List<bitwig.obf.dve> cs = settingsControls();
        int n = 0, ours = 0;
        for (bitwig.obf.dve c : cs) {
            String id = id(c);
            if (id.isEmpty()) continue;
            ours++;
            if (!values.containsKey(id)) continue;
            Object v = Browser.frameworkValue(c);
            if (!(v instanceof bitwig.obf.rMe value)) continue;
            try { value.setValue(values.get(id)); n++; } catch (Throwable t) { Minimal.MOD.log("setting " + id + " failed", t); }
        }
        Minimal.MOD.log("settings page: " + cs.size() + " controls found, " + ours + " of this mod's, " + n + " set" + (anchor.get() == null ? " (the page not seen yet)" : ""));
        return n;
    }

    private static void controls(bitwig.obf.dve w, java.util.List<bitwig.obf.dve> into, int depth) {
        if (depth > 60) return;
        if (w instanceof bitwig.obf.dNg || w instanceof bitwig.obf.dBX) { into.add(w); return; }
        for (Object k : children(w)) if (k instanceof bitwig.obf.dve d) controls(d, into, depth + 1);
    }

    private static void collect(bitwig.obf.dve w, java.util.List<bitwig.obf.dNg> into) {
        if (w instanceof bitwig.obf.dNg s) into.add(s);
        for (Object k : children(w)) if (k instanceof bitwig.obf.dve d) collect(d, into);
    }

    /** A widget's children (dve.YBm: its child list as a stream; vJT is its action bindings, not its children). */
    private static java.util.List<Object> children(bitwig.obf.dve w) {
        try { java.util.stream.Stream<?> st = w.YBm(); return st == null ? java.util.List.of() : st.map(o -> (Object) o).toList(); }
        catch (Throwable t) { return java.util.List.of(); }
    }
}
