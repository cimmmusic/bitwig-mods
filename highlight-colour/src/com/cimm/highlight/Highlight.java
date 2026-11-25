package com.cimm.highlight;

import bitwig.obf.iok; // a colour (r, g, b, a floats: FzB, IwV, UNj, hUR)
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface: Highlight Colour.
 *
 * Bitwig's theme colours are named colour objects (eeY) held in fields of its theme registry (eff), registered in
 * eff.UNj(). Each keeps its colour in a settable slot (efb.MRY(iok)) that notifies the widgets using it. After the
 * registry is built, the accent-family colours are recoloured to the user's choice; changing the setting recolours them
 * again from their original values, and "Theme colour" puts the originals back.
 */
public final class Highlight {
    private Highlight() {}

    static final Bml.Mod MOD = Bml.mod("highlight-colour");
    static final bml.api.Settings STORE = MOD.settings();

    /** The accent family: Bitwig's names for the highlight colour and its variants. */
    static final Set<String> NAMES = Set.of(
            "Accent (default)", "Implicit On (subtle)", "Led On", "Drop Indicator", "Beat Marker Color",
            "Active Toggle Icon (Playing)", "Notification Normal", "Knob Value Color", "Number field bar background",
            "Comp fill", "Device Locked Overlay", "Display Start/End Markers");
    static final String MAIN = "Accent (default)";

    /** Presets: label and colour (0xRRGGBB). Index 0 = the theme's own colour. */
    static final String[] LABELS = {"Theme colour", "Orange", "Red", "Pink", "Purple", "Violet", "Blue", "Cyan",
            "Mint", "Green", "Lime", "Yellow", "White", "Custom hue"};
    static final int[] RGB = {0, 0xFF6A1F, 0xFF3B4A, 0xFF4FA3, 0xB65CFF, 0x7C5CFF, 0x3D7BFF, 0x2ED3F0,
            0x3BE8B0, 0x4CD964, 0xB8F03A, 0xFFD23F, 0xF5F5F5, 0};
    static final int CUSTOM = LABELS.length - 1;

    static int choice() { return Math.max(0, Math.min(CUSTOM, STORE.getInt("choice", 0))); }
    static int hue() { return Math.max(0, Math.min(360, STORE.getInt("hue", 30))); }

    /** The registry's accent colours and their original values (filled once the registry is built). */
    private static final Map<Object, iok> originals = new IdentityHashMap<>();
    private static final Map<Object, String> names = new IdentityHashMap<>();

    private static final java.util.concurrent.atomic.AtomicInteger seen = new java.util.concurrent.atomic.AtomicInteger();

    /** Every theme colour passes here as Bitwig registers it, with its name: note the accent family. */
    @Hook(id = "note-theme-colour", target = bitwig.obf.eeY.class,
          method = "cfM(Ljava/lang/String;Lefd;Liok;FFF)V", at = At.ENTRY)
    public static void colourRegistered(@bml.api.This Object colour, @bml.api.Arg(0) String name) {
        seen.incrementAndGet();
        if (name != null) Recorder.named(colour, name);
        if (name != null) Tone.register(colour, name);
        if (name == null || !NAMES.contains(name)) return;
        synchronized (originals) { names.put(colour, name); }
    }

    /** The registry is built (and any theme applied): read the accent colours' values and recolour them. */
    @Hook(id = "theme-registry-built", target = bitwig.obf.eff.class, method = "UNj()V", at = At.BEFORE_RETURN)
    public static void registryBuilt(@bml.api.This Object registry) {
        try {
            int nulls = 0;
            synchronized (originals) {
                for (var e : names.entrySet()) {
                    if (originals.containsKey(e.getKey())) continue;
                    iok value = ((bitwig.obf.eeY) e.getKey()).iHb(); // its own (absolute) colour
                    if (value == null) { nulls++; continue; }
                    originals.put(e.getKey(), value);
                }
            }
            MOD.log("theme colours registered: " + seen.get() + "; highlight colours: " + originals.size()
                    + (nulls > 0 ? " (" + nulls + " without their own colour)" : "") + " " + names.values());
            apply();
        } catch (Throwable t) { MOD.log("reading the theme colours failed", t); }
    }

    // ---- what Bitwig draws: every theme colour is resolved through eeY.cfM(iok) ----

    /** The accent colours (identity) and whether each is the main accent; swapped as a whole when found. */
    private static volatile Object[] accents = new Object[0];
    private static volatile boolean[] isMain = new boolean[0];
    /** The main accent's colour as the theme set it: the reference the variants are measured against. */
    private static volatile iok main;
    /** The chosen colour, or null for the theme's own. */
    private static volatile Color target;
    /** Recoloured values by source colour (rgba and main flag), so drawing doesn't redo the maths. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, iok> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final iok SAME = new iok(0f, 0f, 0f, 0f); // cache marker: keep the colour as it is

    /** Takes the current choice (and the accent colours, once found); Bitwig repaints with it. */
    static void apply() {
        synchronized (originals) {
            List<Object> list = new ArrayList<>(originals.keySet());
            Object[] a = list.toArray();
            boolean[] m = new boolean[a.length];
            for (int i = 0; i < a.length; i++) {
                m[i] = MAIN.equals(names.get(a[i]));
                if (m[i]) main = originals.get(a[i]);
            }
            accents = a; isMain = m;
        }
        int choice = choice();
        target = choice == CUSTOM ? Color.getHSBColor(hue() / 360f, 0.85f, 1f) : choice == 0 ? null : new Color(RGB[choice]);
        cache.clear();
        MOD.log("highlight " + LABELS[choice] + (choice == CUSTOM ? " " + hue() + "°" : "") + " for " + accents.length + " colours");
    }

    private static int drawLogs;

    /** A theme colour as a widget gets it (eeY.cfM(widget)), after any theme has put its own value in: for the
     *  accent colours, the chosen colour instead. */
    @Hook(id = "draw-highlight", target = bitwig.obf.eeY.class, method = "cfM(Ldve;)Liok;", at = At.WRAP_RETURN)
    public static iok resolved(@bml.api.Return iok value, @bml.api.This bitwig.obf.eeY colour) {
        if (Recorder.on) Recorder.seen(colour, value);
        if (value == null) return value;
        Color t = target;
        if (t != null) {
            Object[] a = accents;
            int k = -1;
            for (int i = 0; i < a.length; i++) if (a[i] == colour) { k = i; break; }
            iok m = main;
            if (k >= 0 && m != null) {
                boolean mainAccent = isMain[k];
                long key = ((long) Float.floatToIntBits(value.FzB()) * 31 + Float.floatToIntBits(value.IwV())) * 31
                        + Float.floatToIntBits(value.UNj()) * 7L + Float.floatToIntBits(value.hUR()) * 3L + (mainAccent ? 1 : 0);
                iok out = cache.get(key);
                if (out == null) {
                    iok r = recolour(value, m, t, mainAccent);
                    out = r == null ? SAME : r;
                    if (cache.size() < 4096) cache.put(key, out);
                    if (drawLogs < 4) { drawLogs++; MOD.log("drawing " + names.get(colour) + " in the highlight colour" + (r == null ? " (kept: not a shade of the accent)" : "")); }
                }
                return out == SAME ? value : out;
            }
        }
        return Tone.adjust(colour, value);
    }

    /** The widget asking for a colour: notes its window now and then (to repaint all windows when a setting changes). */
    @Hook(id = "colour-asked", target = bitwig.obf.eeY.class, method = "cfM(Ldve;)Liok;", at = At.ENTRY)
    public static void colourAsked(@bml.api.Arg(0) Object widget) { Tone.noteWidget(widget); }

    // ---- text on coloured items: black or white, whichever contrasts more ----

    /*
     * Bitwig picks the text for a coloured item (a track, clip, scene...) in eff.cfM(colour): Dark Text when the colour's
     * perceived brightness (iok.MRY()) is above 0.55, else Light Text; eff.cfM(colour, dark, light) does the same with
     * given colours. The brightness is replaced by the WCAG choice (black text when the colour's relative luminance is
     * above 0.179, where black contrasts more than white), and the light text by "Lighter Text" (always white, never
     * flipped by the background tone).
     */
    @Hook(id = "contrast-text", target = bitwig.obf.eff.class, method = "cfM(Liok;)LeeY;",
          at = At.REPLACE_CALL, callOwner = iok.class, call = "MRY()F")
    public static float contrastText(iok colour) { return brightness(colour); }

    @Hook(id = "contrast-choice", target = bitwig.obf.eff.class, method = "cfM(Liok;Liok;Liok;)Liok;",
          at = At.REPLACE_CALL, callOwner = iok.class, call = "MRY()F")
    public static float contrastChoice(iok colour) { return brightness(colour); }

    static float brightness(iok c) {
        try { return Tone.luminance(c) > 0.179 ? 0.6f : 0.5f; } catch (Throwable t) { return c.MRY(); }
    }

    @Hook(id = "contrast-light-text", target = bitwig.obf.eff.class, method = "cfM(Liok;)LeeY;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "MYI()LeeY;")
    public static bitwig.obf.eeY lightText(bitwig.obf.eff registry) {
        try { bitwig.obf.eeY c = registry.cfM("Lighter Text"); if (c != null) return c; } catch (Throwable ignored) {}
        return registry.MYI();
    }

    /**
     * original's colour moved to the target: the target's hue, with original's brightness and saturation relative to the
     * main accent (so dimmer variants stay dimmer) and its own transparency. null when original isn't accent-like (a
     * theme that set it to white or grey, say): it then stays as it is.
     */
    static iok recolour(iok original, iok main, Color target, boolean isMain) {
        float[] o = Color.RGBtoHSB(c(original.FzB()), c(original.IwV()), c(original.UNj()), null);
        float[] a = Color.RGBtoHSB(c(main.FzB()), c(main.IwV()), c(main.UNj()), null);
        float[] t = Color.RGBtoHSB(target.getRed(), target.getGreen(), target.getBlue(), null);
        if (!isMain) {
            float dh = Math.abs(o[0] - a[0]); dh = Math.min(dh, 1 - dh);
            if (o[1] < 0.3f || dh > 30f / 360f) return null; // not a shade of the accent
        }
        float sat = t[1] * Math.min(1f, a[1] <= 0 ? 1f : o[1] / a[1]);
        float bri = Math.min(1f, t[2] * (a[2] <= 0 ? 1f : o[2] / a[2]));
        int rgb = Color.HSBtoRGB(t[0], sat, bri);
        return new iok(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, original.hUR());
    }

    // ---- dark scene backgrounds (see Scenes); every hook of the mod lives in this class: BML takes one helper class ----

    @Hook(id = "scene-paint", target = bitwig.obf.bdO.class, method = "a_(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void painting(@bml.api.This Object widget) {
        Scenes.scene.set(Scenes.on && widget instanceof bitwig.obf.bdw ? Boolean.TRUE : null);
    }

    @Hook(id = "scene-colour", target = bitwig.obf.bdO.class, method = "a_(Lcom/bitwig/graphics/SYo;)V",
          at = At.REPLACE_CALL, callOwner = com.bitwig.graphics.SYo.class, call = "Hnt(Liok;)V")
    public static void colour(com.bitwig.graphics.SYo g, iok c) {
        if (Scenes.scene.get() != null) {
            Scenes.scene.set(null);
            iok d = Scenes.darker(c);
            if (d != null) {
                if (Scenes.logs < 2) { Scenes.logs++; MOD.log("scene background " + Scenes.hex(c) + " drawn as " + Scenes.hex(d)); }
                g.Hnt(d);
                return;
            }
        }
        g.Hnt(c);
    }

    // ---- module previews (see Previews) ----

    @Hook(id = "module-preview", target = com.bitwig.flt.widget.core.device.KL3.class,
          method = "cfM(Lcom/bitwig/graphics/BrJ;)Ldve;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.dqW.class, call = "cfM(Lcom/bitwig/graphics/BrJ;)LdqW;")
    public static bitwig.obf.dqW modulePreview(com.bitwig.graphics.BrJ bitmap) {
        Previews.recolour(bitmap);
        return bitwig.obf.dqW.cfM(bitmap);
    }

    // ---- double-click a Background slider: back to the default (dark) ----

    /** A press on a slider (dNg): on a double-click on one of the Background sliders, all four go back to default. */
    @Hook(id = "slider-press", target = bitwig.obf.dNg.class, method = "MRY(LeoX;DDLeoW;I)LdrX;", at = At.ENTRY)
    public static void sliderPress(@bml.api.This Object slider, @bml.api.Arg(4) int clicks) {
        if (clicks != 2 || !(slider instanceof bitwig.obf.dNg s)) return;
        try { Tone.resetFrom(s); } catch (Throwable t) { MOD.log("resetting the background failed", t); }
    }

    /*
     * Minimal's "Dark Text" is white: Bitwig uses it on surfaces that are light grey in its own theme (value fields,
     * notifications) and dark in Minimal. Bright coloured items still need real dark text, so the dark choice in
     * eff.cfM(colour) gets its own near-black colour.
     */
    static final String INK = "Contrast Dark Ink";
    private static volatile bitwig.obf.eeY ink;
    static bitwig.obf.eeY ink() {
        bitwig.obf.eeY i = ink;
        if (i == null) ink = i = bitwig.obf.eeY.cfM(INK, new iok(0.067f, 0.067f, 0.067f, 1f));
        return i;
    }

    @Hook(id = "contrast-dark-text", target = bitwig.obf.eff.class, method = "cfM(Liok;)LeeY;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "Ktv()LeeY;")
    public static bitwig.obf.eeY darkText(bitwig.obf.eff registry) {
        try { return ink(); } catch (Throwable t) { return registry.Ktv(); }
    }

    // ---- notifications: body text that reads on the notification's own background ----

    /*
     * Bitwig's notifications (dMF, and the progress one eaA) write their text in Dark Text / Subtle Dark Text, made
     * for its default light grey Notification Background. On a dark one that text vanished. The colour is chosen
     * here instead: the notification's background as drawn (theme and background tone applied) decides, by contrast,
     * between that dark text and white (Lighter Text).
     */
    private static final ThreadLocal<Object> notification = new ThreadLocal<>();

    @Hook(id = "notification-build", target = bitwig.obf.dMF.class, method = "MRY(LcpI;)V", at = At.ENTRY)
    public static void notificationBuild(@bml.api.This Object widget) { notification.set(widget); }

    @Hook(id = "progress-build", target = bitwig.obf.eaA.class, method = "MRY(LYFZ;)V", at = At.ENTRY)
    public static void progressBuild(@bml.api.This Object widget) { notification.set(widget); }

    @Hook(id = "notification-subtle-text", target = bitwig.obf.dMF.class, method = "MRY(LcpI;)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "d3()LeeY;")
    public static bitwig.obf.eeY notificationSubtle(bitwig.obf.eff r) { return readable(r, r.d3()); }

    @Hook(id = "progress-subtle-text", target = bitwig.obf.eaA.class, method = "MRY(LYFZ;)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "d3()LeeY;")
    public static bitwig.obf.eeY progressSubtle(bitwig.obf.eff r) { return readable(r, r.d3()); }

    @Hook(id = "progress-text", target = bitwig.obf.eaA.class, method = "MRY(LYFZ;)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "Ktv()LeeY;")
    public static bitwig.obf.eeY progressText(bitwig.obf.eff r) { return readable(r, r.Ktv()); }

    private static bitwig.obf.eeY readable(bitwig.obf.eff r, bitwig.obf.eeY dark) {
        try {
            iok bg = null;
            if (notification.get() instanceof bitwig.obf.dve w) bg = r.cfM("Notification Background").cfM(w);
            boolean darkBg = bg != null ? Tone.luminance(bg) <= 0.179 : !Tone.isLight();
            if (darkBg) { bitwig.obf.eeY white = r.cfM("Lighter Text"); if (white != null) return white; }
            else if (Tone.luminance(dark.cfM((bitwig.obf.dve) notification.get())) > 0.4) return ink();
        } catch (Throwable t) { MOD.log("notification text colour failed", t); }
        return dark;
    }

    private static int c(float v) { return Math.round(Math.max(0, Math.min(1, v)) * 255); }
}
