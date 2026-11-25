package com.cimm.minimal;

import com.bitwig.graphics.SYo; // the graphics a widget paints with
import java.util.EnumSet;

import bitwig.obf.IdX; // a resource name (here: a font file name in Bitwig's fonts folder)
import bitwig.obf.dpK; // Bitwig's font cache: loads each font file once, by name
import bitwig.obf.iok; // a colour (r, g, b, a floats: FzB, IwV, UNj, hUR)
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.graphics.sQv; // a loaded font face
import com.bitwig.graphics.sal; // the graphics system (makes font faces from files)
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Minimal Dark: every hook of the theme in one class (BML takes one helper class per mod). Sections, from the mods
 * this was merged from: the theme colours and resources (Theme), the interface font (UiFont), the highlight colour,
 * background tone and contrast (Highlight), and the dim behind menus and popup fades (Backdrop).
 */
public final class Minimal {
    private Minimal() {}

    // ================================================================ from Highlight
    /**
     * Interface: Highlight Colour.
     *
     * Bitwig's theme colours are named colour objects (eeY) held in fields of its theme registry (eff), registered in
     * eff.UNj(). Each keeps its colour in a settable slot (efb.MRY(iok)) that notifies the widgets using it. After the
     * registry is built, the accent-family colours are recoloured to the user's choice; changing the setting recolours them
     * again from their original values, and "Theme colour" puts the originals back.
     */


    static final Bml.Mod MOD = Bml.mod("minimal-dark");
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
        if (Theme.any()) Theme.registering.set(name);
        if (name != null) Recorder.named(colour, name);
        if (name != null) Tone.register(colour, name);
        if (name == null || !NAMES.contains(name)) return;
        synchronized (originals) { names.put(colour, name); }
    }

    // ---- the theme (see Theme) ----

    /** A named colour has stored Bitwig's value (its registration is returning): the theme's value goes in instead. */
    @Hook(id = "theme-colour", target = bitwig.obf.eeY.class,
          method = "cfM(Ljava/lang/String;Lefd;Liok;FFF)V", at = At.BEFORE_RETURN)
    public static void themeColour(@bml.api.This bitwig.obf.eeY colour) {
        if (Theme.any()) Theme.registered(colour);
    }

    /** "Gradient: false": a shade derived by adding to hue, saturation and brightness is the colour itself. */
    @Hook(id = "theme-no-gradient-add", target = iok.class, method = "hUR(FFF)Liok;", at = At.WRAP_RETURN)
    public static iok themeGradientAdd(@bml.api.Return iok derived, @bml.api.This iok original) {
        return Theme.gradient(derived, original);
    }

    /** "Gradient: false": a shade derived by scaling saturation is the colour itself. */
    @Hook(id = "theme-no-gradient-scale", target = iok.class, method = "TMF(FFF)Liok;", at = At.WRAP_RETURN)
    public static iok themeGradientScale(@bml.api.Return iok derived, @bml.api.This iok original) {
        return Theme.gradient(derived, original);
    }

    /** A Bitwig resource opened for reading: the theme's overlay icon or image of the same path replaces it. */
    @Hook(id = "theme-overlays", target = bitwig.obf.YBi.class, method = "cfM()Ljava/io/InputStream;", at = At.WRAP_RETURN)
    public static InputStream themeOverlay(@bml.api.Return InputStream stream, @bml.api.This bitwig.obf.YBi file) {
        return Theme.resource(stream, file);
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
        int[] dd = depth.get();
        if (dd[0] > 0) dd[0]--;
        value = Theme.relative(colour, value);
        if (colour == dotColour && dotColour != null) {
            boolean shown = false;
            try {
                Object w = asking.get();
                bitwig.obf.dve d = w instanceof bitwig.obf.dve v ? v : null;
                for (int i = 0; i < 4 && d != null; i++, d = d.RbC())
                    if (d instanceof bitwig.obf.dMN n) { shown = n.d3(); break; }
                if (dotLogs++ < 4) MOD.log("notifications dot: " + (shown ? "shown (mint)" : "hidden (grey)"));
            } catch (Throwable ignored) {}
            return shown ? new iok(MINT.FzB(), MINT.IwV(), MINT.UNj(), 1f) : DOT_GREY;
        }
        if (loopArr.length > 0) {
            Object w = asking.get(); // (left set: a colour made from another resolves inside, for the same widget)
            // the loop is on: its icon asks for Bitwig's "on" colour (not the hover colour, which stays the highlight)
            boolean inLoop = value != null && inLoopButton(w);
            if (inLoop && loopSeen.size() < 24 && loopSeen.add("asks " + colour))
                MOD.log("loop button asks for " + colour + " = " + Math.round(value.FzB() * 255) + "," + Math.round(value.IwV() * 255) + "," + Math.round(value.UNj() * 255));
            // the loop is on: the icon's "on" colour (Accent (hitech) here; not the hover colour, which stays the highlight)
            if (inLoop && LOOP_ON.contains(String.valueOf(colour)) && loopOn()) {
                if (loopSeen.size() < 24 && loopSeen.add("resolved " + colour)) MOD.log("loop on: " + colour + " drawn mint");
                return new iok(MINT.FzB(), MINT.IwV(), MINT.UNj(), value.hUR());
            }
        }
        return styled(value, colour);
    }

    /** The highlight recolour and the background tone, for a colour as resolved by either path. */
    private static final java.util.Set<String> laneSeen = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static iok styled(iok value, bitwig.obf.eeY colour) {
        if (Recorder.on) Recorder.seen(colour, value);
        if (value == null) return value;
        if (Tone.active() && laneSeen.size() < 40) {
            int px = Math.round(value.FzB() * 255);
            if ((px == 0x13 || px == 0x1A) && Math.round(value.IwV() * 255) == px && laneSeen.add(String.valueOf(colour))) {
                iok out = Tone.adjust(colour, value);
                MOD.log("grey " + Scenes.hex(value) + " from \"" + colour + "\" kind " + Tone.kindOf(colour) + " -> " + Scenes.hex(out));
            }
        }
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

    /*
     * Widget backgrounds (the arranger's lanes and rows, panels...) resolve their colour against a base colour,
     * eeY.cfM(base), not per widget: the same styling applies there. Colours made from other colours resolve inside each
     * other (and inside eeY.cfM(widget)): inside a per-widget resolution (depth[0] > 0) that one styles instead.
     */
    private static final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[2]);

    @Hook(id = "colour-base-1", target = bitwig.obf.eeY.class, method = "cfM(Liok;)Liok;", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.eeY.class, call = "iHb()Liok;", occurrence = 1)
    public static iok colourBase1(bitwig.obf.eeY colour) { return baseValue(colour); }

    @Hook(id = "colour-base-2", target = bitwig.obf.eeY.class, method = "cfM(Liok;)Liok;", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.eeY.class, call = "iHb()Liok;", occurrence = 2)
    public static iok colourBase2(bitwig.obf.eeY colour) { return baseValue(colour); }

    /** A colour's own value as eeY.cfM(base) reads it: styled, unless a per-widget resolution around it styles instead. */
    private static iok baseValue(bitwig.obf.eeY colour) {
        iok value = colour.iHb();
        if (value == null || depth.get()[0] > 0) return value;
        try { return styled(Theme.relative(colour, value), colour); } catch (Throwable t) { return value; }
    }

    /** The widget asking for a colour: notes its window now and then (to repaint all windows when a setting changes). */
    @Hook(id = "colour-asked", target = bitwig.obf.eeY.class, method = "cfM(Ldve;)Liok;", at = At.ENTRY)
    public static void colourAsked(@bml.api.Arg(0) Object widget) {
        int[] dd = depth.get();
        if (++dd[0] > 64) dd[0] = 1;
        if (widget instanceof bitwig.obf.dve w0) {
            // a settings slider or dropdown, itself or a few levels up from what is asking (for the Looks to move them)
            bitwig.obf.dve w = w0;
            for (int i = 0; i < 4 && w != null; i++, w = w.RbC())
                if (w instanceof bitwig.obf.dNg || w instanceof bitwig.obf.dBX) { Tone.noteControl(w); break; }
        }
        Tone.noteWidget(widget);
        if (loopArr.length > 0 || dotColour != null) asking.set(widget);
    }

    /** The widget asking for a colour right now (between colour-asked and draw-highlight), when loop buttons exist. */
    private static final ThreadLocal<Object> asking = new ThreadLocal<>();
    /** The transport loop buttons (one or two): a copy for cheap checks while drawing. */
    private static volatile Object[] loopArr = new Object[0];

    /** Whether w is a loop button or sits within a few levels of one. */
    private static boolean inLoopButton(Object w) {
        Object[] lb = loopArr;
        if (lb.length == 0 || !(w instanceof bitwig.obf.dve d)) return false;
        for (int i = 0; i < 4 && d != null; i++, d = d.RbC()) for (Object b : lb) if (b == d) return true;
        return false;
    }

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
    /** A press on a settings slider; two on the same slider within 400 ms are a double-click (the handler's int
     *  argument is not a click count). */
    @Hook(id = "slider-press", target = bitwig.obf.dNg.class, method = "MRY(LeoX;DDLeoW;I)LdrX;", at = At.ENTRY)
    public static void sliderPress(@bml.api.This Object slider, @bml.api.Arg(0) Object button) {
        if (!(slider instanceof bitwig.obf.dNg s) || button != bitwig.obf.eoX.cfM) return;
        long now = System.nanoTime();
        boolean twice = slider == lastSlider && now - lastPress < 400_000_000L;
        lastSlider = twice ? null : slider; lastPress = now;
        if (!twice) return;
        // after the press is handled (it starts a drag that could set the value where the mouse is)
        try {
            java.util.TimerTask[] once = new java.util.TimerTask[1];
            java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
            once[0] = bitwig.obf.dpN.TVZ().cfM(() -> {
                if (once[0] != null) once[0].cancel();
                if (done.getAndSet(true)) return; // once, however often the timer fires
                try { Tone.resetFrom(s); } catch (Throwable t) { MOD.log("resetting the background failed", t); }
            }, 120, 100000);
        } catch (Throwable t) {
            try { Tone.resetFrom(s); } catch (Throwable u) { MOD.log("resetting the background failed", u); }
        }
    }
    private static volatile Object lastSlider;
    private static volatile long lastPress;

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

    // ================================================================ from UiFont
    /**
     * Interface: Font.
     *
     * Bitwig draws text with font families (efs: Lato for the UI, CP Mono for numbers) whose files it loads
     * by name through one cache, dpK.Hnt(IdX): it finds fonts/&lt;name&gt; and asks the graphics system for a face,
     * from the File when there is one, else from a stream. Both calls are replaced here: when the user chose a
     * font for that file, the face is made from the user's file instead. Fallback fonts (other scripts) and
     * Bitwig's icon font are left alone.
     */


    /** Bitwig's graphics system, seen when it loads its fonts: the font list makes its previews with it. */
    static volatile sal graphics;

    /**
     * A dropdown fills its popup with dBX.cfM(popup) when it opens. For this mod's Settings dropdowns the popup gets
     * the font list instead (names drawn in their own fonts, a search field); every other dropdown is left alone.
     */
    @Hook(id = "font-dropdown", target = bitwig.obf.dBX.class, method = "JJY()LdKW;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.dBX.class, call = "cfM(LdKW;)V")
    public static void fillDropdown(bitwig.obf.dBX dropdown, bitwig.obf.dKW popup) {
        Browser.Target t = null;
        try { t = Browser.target(dropdown); } catch (Throwable ignored) {}
        if (t != null) {
            try { Browser.fill(dropdown, popup, t); return; }
            catch (Throwable e) { Fonts.MOD.log("font list failed, showing the plain list", e); }
        }
        dropdown.cfM(popup);
    }

    @Hook(id = "font-from-file", target = dpK.class, method = "Hnt(LIdX;)Lcom/bitwig/graphics/sQv;",
          at = At.REPLACE_CALL, callOwner = sal.class, call = "cfM(Ljava/io/File;)Lcom/bitwig/graphics/sQv;")
    public static sQv fontFromFile(sal graphics, File file) throws IOException {
        Minimal.graphics = graphics;
        File mine = Fonts.replacement(file == null ? null : file.getName());
        if (mine != null) {
            try { return Shift.mark(graphics.cfM(mine)); }
            catch (Throwable t) { Fonts.MOD.log("couldn't load " + mine + ", using Bitwig's " + file.getName(), t); }
        }
        return graphics.cfM(file);
    }

    @Hook(id = "font-from-stream", target = dpK.class, method = "Hnt(LIdX;)Lcom/bitwig/graphics/sQv;",
          at = At.REPLACE_CALL, callOwner = sal.class,
          call = "cfM(Ljava/lang/String;Ljava/io/InputStream;)Lcom/bitwig/graphics/sQv;")
    public static sQv fontFromStream(sal graphics, String name, InputStream in) throws IOException {
        Minimal.graphics = graphics;
        File mine = Fonts.replacement(name);
        if (mine != null) {
            try (InputStream own = new FileInputStream(mine)) { return Shift.mark(graphics.cfM(name, own)); }
            catch (Throwable t) { Fonts.MOD.log("couldn't load " + mine + ", using Bitwig's " + name, t); }
        }
        return graphics.cfM(name, in);
    }

    // ------------------------------------------------------------------------------------------- text size

    /**
     * Bitwig builds each font family (efs) by adding its files with a size factor: efs.cfM(family, file, factor) calls
     * family.cfM(file, coverage, factor); the renderer draws and measures each file at size x factor (Bitwig uses it to
     * shrink fallback fonts to match). For the family's own main file (Lato for the interface and bold, CP Mono for
     * numbers) the factor is multiplied by the user's size for that role. Fallbacks and the icon font keep theirs.
     */
    @Hook(id = "text-size", target = bitwig.obf.efs.class, method = "cfM(Lcom/bitwig/graphics/OLR;Ljava/lang/String;D)V",
          at = At.REPLACE_CALL, callOwner = com.bitwig.graphics.OLR.class,
          call = "cfM(Ljava/lang/String;Ljava/util/function/IntPredicate;D)V")
    public static void textSize(com.bitwig.graphics.OLR family, String file, java.util.function.IntPredicate coverage, double factor) {
        double f = factor;
        try {
            if (file != null && file.equals(family.cfM())) { // the family's main file, not a fallback
                double k = Fonts.sizeFactor(file);
                if (k != 1) { f = factor * k; Fonts.MOD.log(file + " size " + Math.round(k * 100) + "%"); }
            }
        } catch (Throwable t) { Fonts.MOD.log("text size failed for " + file, t); }
        family.cfM(file, coverage, f);
    }

    // ------------------------------------------------------------------------------------------- moving text down

    /*
     * Bitwig draws a line of text as runs, one per font face (the UI font, then fallbacks such as its icon font for
     * symbols): RKg sets the run's face on the graphics (JXO.cfM(hLr)), then draws the run (JXO.MRY(x, y, text, colour)).
     * The first call notes the face; the second moves the run down when the face is one of the user's fonts, whose
     * letters sit higher than Lato's. Icons and other fallbacks stay where they are.
     */

    private static final String RUN = "cfM(Lcom/bitwig/graphics/render_commands/JXO;[DDLiok;Lcom/bitwig/graphics/render_commands/hLr;Ljava/lang/String;)V";

    @Hook(id = "run-face", target = com.bitwig.graphics.render_commands.RKg.class, method = RUN, at = At.REPLACE_CALL,
          callOwner = com.bitwig.graphics.render_commands.JXO.class, call = "cfM(Lcom/bitwig/graphics/render_commands/hLr;)V")
    public static void runFace(com.bitwig.graphics.render_commands.JXO g, com.bitwig.graphics.render_commands.hLr face) {
        Shift.current.set(face);
        g.cfM(face);
    }

    @Hook(id = "run-draw", target = com.bitwig.graphics.render_commands.RKg.class, method = RUN, at = At.REPLACE_CALL,
          callOwner = com.bitwig.graphics.render_commands.JXO.class, call = "MRY(DDLjava/lang/String;Liok;)V")
    public static void runDraw(com.bitwig.graphics.render_commands.JXO g, double x, double y, String text, bitwig.obf.iok colour) {
        com.bitwig.graphics.render_commands.hLr run = Shift.current.get();
        double d = Shift.offset(run);
        if (d > 0) d = Shift.fit(g, run, y, d); // never past the bottom of the field / label: descenders stay whole
        g.MRY(x, y + d, text, colour);
        // dark text (black on bright tracks, clips, light backgrounds): a thin face leaves its strokes part-covered,
        // so black over orange reads brown; a second pass a fraction of a pixel across fills the strokes in
        if (Fonts.solidDark && colour != null && Shift.userFace(run) && dark(colour))
            g.MRY(x + 0.35, y + d, text, colour);
    }

    private static boolean dark(bitwig.obf.iok c) {
        return c.hUR() > 0.9f && c.FzB() < 0.2f && c.IwV() < 0.2f && c.UNj() < 0.2f;
    }

    /** Toolbar menus in sentence case (see Labels): YGP.cfM(files) merges the language's text files into one map,
     *  reading each with YGP.cfM(file). All of the mod's hooks live in this class: BML takes one helper class per mod. */
    @Hook(id = "sentence-case-toolbar", target = bitwig.obf.YGP.class, method = "cfM(Ljava/util/List;)Ljava/util/Map;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.YGP.class, call = "cfM(LYBi;)Ljava/util/Map;")
    @SuppressWarnings("rawtypes")
    public static java.util.Map textsLoaded(bitwig.obf.YBi file) throws IOException {
        return Labels.sentenceCase(bitwig.obf.YGP.cfM(file));
    }

    /** Wheel / trackpad scrolls, before Bitwig handles them: the open font list scrolls. An ENTRY hook, so other mods'
     *  hooks on the same dispatch (the mixer mini views' scroll) are unaffected. */
    @Hook(id = "list-wheel", target = bitwig.obf.dpl.class, method = "cfM(Leph;)V", at = At.ENTRY)
    public static void wheel(@bml.api.Arg(0) bitwig.obf.eph e) { Browser.wheel(e); }

    // ================================================================ from Backdrop
    /**
     * Interface: Menu Backdrop.
     *
     * Bitwig's menus are windows of their own (dsQ); dsQ.t_(visible) shows and hides them, and the popup host opens each one
     * with its content (dnf.cfM(content, placement, focus)): menus are dKW, hover popups and tooltips are other kinds and are
     * ignored. While a menu is open, a dim layer (Bitwig's own popup overlay colour, "Popup overlay background color") sits
     * on each main window's layer stack above everything, fading in and out. The fade's frames are asked for from Bitwig's
     * UI timer (repaints asked for during a paint are dropped). All hooks live in this class (one helper class per mod).
     */



    static final String[] STYLES = {"Dim", "Off"};
    static final int DIM = 0, OFF = 1;
    /** The button that opened the menu stays above the dim (setting). */
    static volatile boolean raiseButton = STORE.getBoolean("raise_button", true);
    static volatile int style = Math.max(0, Math.min(OFF, STORE.getInt("style", DIM)));

    private static final double IN_MS = 180, OUT_MS = 150;

    /** Menus shown now. */
    private static final Set<Object> open = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Main windows seen painting (to repaint them when a menu opens or closes, and while fading). */
    private static final Set<Object> windows = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    // fade state: the level (0..1) at the last change, when, and the direction
    private static volatile boolean shown;
    private static volatile double level0;
    private static volatile long changed;

    private static int logs;
    private static void log(String s) { if (logs < 30) { logs++; MOD.log(s); } }
    private static void log(String s, Throwable t) { if (logs < 30) { logs++; MOD.log(s, t); } }

    static double level() {
        double t = (System.nanoTime() - changed) / 1e6;
        return shown ? Math.min(1, level0 + t / IN_MS) : Math.max(0, level0 - t / OUT_MS);
    }

    // ------------------------------------------------------------------------------------------- menus

    /** Popup windows that hold a menu (only these dim the window: hover popups and tooltips don't). */
    private static final Set<Object> menus = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** What the popup host is opening right now (dnf.cfM(content, placement, focus) creates the window and shows it). */
    private static final ThreadLocal<Object> opening = new ThreadLocal<>();

    @Hook(id = "popup-opening", target = bitwig.obf.dnf.class, method = "cfM(LdrO;LdsP;Z)V", at = At.ENTRY)
    public static void popupOpening(@bml.api.Arg(0) Object content, @bml.api.Arg(1) Object placement) {
        opening.set(content);
        openingAnchor.set(content instanceof bitwig.obf.dKW ? anchorOf(placement) : null);
    }

    // ------------------------------------------------------------------------------------------- the menu's button

    /*
     * A menu opened from a button (File, Play... in the toolbar) is placed against that button: the placement (a dsP,
     * e.g. dPi) holds it in a widget field. That button is painted again on top of the dim, so the clicked menu name
     * stays in the foreground in its highlight colour while everything else dims.
     */
    private static final ThreadLocal<Object> openingAnchor = new ThreadLocal<>();
    private static int anchorLogs;
    /** Open menu popup -> the button it opened from. */
    static final java.util.Map<Object, bitwig.obf.dve> anchors = Collections.synchronizedMap(new WeakHashMap<>());

    private static bitwig.obf.dve anchorOf(Object placement) {
        if (placement == null) return null;
        try {
            for (Class<?> c = placement.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !bitwig.obf.dve.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object w = f.get(placement);
                    // a button: not a whole panel or track row (context menus are placed against those too)
                    // a menu button (dLh: File, Play, Add...) always; another widget only when it is button-sized
                    if (w instanceof bitwig.obf.dLh || (w instanceof bitwig.obf.dve b0 && b0.yiX() > 0 && b0.yiX() <= 80 && b0.Sxo() <= 400)) {
                        bitwig.obf.dve b = (bitwig.obf.dve) w;
                        if (anchorLogs++ < 6) MOD.log("menu opened from " + b.getClass().getName() + " " + Math.round(b.Sxo()) + "x" + Math.round(b.yiX()));
                        return b;
                    }
                    if (w != null && anchorLogs++ < 6) MOD.log("menu anchor too big to raise: " + w.getClass().getName());
                }
        } catch (Throwable t) { log("menu button not found", t); }
        return null;
    }

    /** The buttons of the menus open now, to paint over the dim. */
    static java.util.List<bitwig.obf.dve> openAnchors() {
        java.util.List<bitwig.obf.dve> out = new java.util.ArrayList<>();
        Object[] ps;
        synchronized (open) { ps = open.toArray(); }
        for (Object p : ps) { bitwig.obf.dve a = anchors.get(p); if (a != null) out.add(a); }
        return out;
    }

    @Hook(id = "popup-visible", target = bitwig.obf.dsQ.class, method = "t_(Z)V", at = At.ENTRY)
    public static void popupVisible(@bml.api.This Object popup, @bml.api.Arg(0) boolean visible) {
        try {
            if (visible) {
                Object content = opening.get();
                if (content != null) {
                    opening.set(null);
                    if (content instanceof bitwig.obf.dKW) menus.add(popup); else menus.remove(popup);
                    bitwig.obf.dve a = (bitwig.obf.dve) openingAnchor.get();
                    openingAnchor.set(null);
                    if (a != null && raiseButton) anchors.put(popup, a); else anchors.remove(popup);
                }
                if (!menus.contains(popup)) return; // a hover popup or tooltip
                open.add(popup);
            } else open.remove(popup);
            update(visible ? "shown" : "hidden");
        } catch (Throwable t) { log("popup tracking failed", t); }
    }

    /** A popup thrown away (choosing a menu item can close the menu this way, without hiding it first). */
    @Hook(id = "popup-disposed", target = bitwig.obf.dsQ.class, method = "ix_()V", at = At.ENTRY)
    public static void popupDisposed(@bml.api.This Object popup) {
        try { if (open.remove(popup)) update("disposed"); } catch (Throwable t) { log("popup tracking failed", t); }
    }

    /** Forgets menus that are no longer on screen, whatever closed them. */
    private static void prune() {
        if (open.isEmpty()) return;
        boolean removed;
        synchronized (open) {
            removed = open.removeIf(p -> { try { return !((bitwig.obf.dvW) p).uev(); } catch (Throwable t) { return true; } });
        }
        if (removed) update("gone");
    }

    /** Starts the fade in or out when menus appear or all are gone. */
    private static synchronized void update(String why) {
        boolean any = !open.isEmpty();
        if (any == shown) return;
        double now = level();
        level0 = now; changed = System.nanoTime(); shown = any;
        if (logs < 10) log("menu " + why + "; backdrop " + (any ? "in" : "out"));
        if (any && style != OFF) addLayers();
        startTicker();
    }


    /** Repaints the windows about 60 times a second until the fade is done (from Bitwig's timer, on its UI thread). */
    private static java.util.TimerTask ticker;
    private static synchronized void startTicker() {
        if (ticker != null) return;
        try {
            ticker = bitwig.obf.dpN.TVZ().cfM(Minimal::tick, 0, 16);
        } catch (Throwable t) { log("fade timer unavailable", t); }
    }
    /** One frame: forget closed menus, repaint the dim layers while fading, take them away once faded out. */
    private static void tick() {
        try { prune(); } catch (Throwable ignored) {}
        double a = level();
        Object[] ls;
        synchronized (layers) { ls = layers.values().toArray(); }
        for (Object l : ls) try { ((bitwig.obf.dve) l).jbi(); } catch (Throwable ignored) {}
        if (!shown && a <= 0) { removeLayers(); stopTicker(); }
    }

    private static synchronized void stopTicker() {
        if (ticker != null) { try { ticker.cancel(); } catch (Throwable ignored) {} ticker = null; }
    }

    // ------------------------------------------------------------------------------------------- windows

    /** Notes the main windows as they paint (the dim layer goes on each); popups are windows too and are skipped. */
    @Hook(id = "window-paint", target = bitwig.obf.duG.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void windowPaint(@bml.api.This Object window) {
        if (!(window instanceof bitwig.obf.dsR)) windows.add(window);
    }

    // ------------------------------------------------------------------------------------------- the dim layer

    /*
     * The dim is a layer of its own on each window's layer stack (duG.sc(), the stack Bitwig puts its own in-window
     * popups on, above the main content), sized to the whole stack (dqt.layout). Everything in the window, meters
     * included, is under it. It takes no mouse events (DimLayer), and is taken off once the fade-out is over.
     */
    private static final java.util.Map<Object, Object> layers = Collections.synchronizedMap(new WeakHashMap<>());

    private static void addLayers() {
        Object[] ws;
        synchronized (windows) { ws = windows.toArray(); }
        for (Object o : ws) {
            if (!(o instanceof bitwig.obf.duG w) || layers.containsKey(w)) continue;
            try {
                bitwig.obf.dqs stack = w.sc();
                if (stack == null) continue;
                DimLayer layer = new DimLayer(w);
                stack.cfM(layer, l -> l.cfM(0, 0, stack.Sxo(), stack.yiX()));
                layers.put(w, layer);
            } catch (Throwable t) { log("dim layer failed", t); }
        }
    }

    private static void removeLayers() {
        Object[][] es;
        synchronized (layers) {
            es = layers.entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toArray(Object[][]::new);
            layers.clear();
        }
        for (Object[] e : es) {
            try { ((bitwig.obf.duG) e[0]).sc().MJ((bitwig.obf.dve) e[1]); } catch (Throwable t) { log("removing the dim layer failed", t); }
        }
    }

    /** The dim's colour now: the popup overlay colour at the fade's level (eased); null when nothing shows. */
    static iok dimColour(bitwig.obf.duG w) {
        double a = level();
        if (style == OFF || a <= 0) return null;
        double e = 1 - Math.pow(1 - a, 3);
        iok c = overlayColour(w);
        return new iok(c.FzB(), c.IwV(), c.UNj(), (float) (c.hUR() * e));
    }

    /** Bitwig's own colour for what lies behind a popup ("Popup overlay background color", themeable). */
    static iok overlayColour(bitwig.obf.duG w) {
        try {
            iok c = w.PEn().MLK().cfM(w);
            if (c != null) return c;
        } catch (Throwable t) { log("popup overlay colour unavailable; using black 80%", t); }
        return new iok(0.03f, 0.03f, 0.03f, 0.8f);
    }

    // ------------------------------------------------------------------------------------------- popups fade in

    /*
     * Popups are native windows: CocoaWindowHandle.Hnt(visible) shows one. On entry, for a popup window (Bitwig's
     * PopupWindowHandle) its native window (IBe.Hnt()) is made see-through, then raised to fully opaque over FADE_MS
     * from Bitwig's UI timer (NativeAlpha: [NSWindow setAlphaValue:] or [layer setOpacity:]).
     */
    static volatile boolean fadePopups = STORE.getBoolean("fade_popups", true);
    private static final double FADE_MS = 130;
    private static final java.util.Map<Long, Long> fading = new java.util.concurrent.ConcurrentHashMap<>();
    private static int fadeLogs;

    /** Popups fade in only on macOS (the window opacity call is Cocoa's); elsewhere they just appear. */
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");

    /*
     * A window is shown or hidden through its handle (TopLevelWindowHandle.cfM(visible), the same on every platform),
     * called from Bitwig's window class (duG) in two places. Before a popup is shown, it goes see-through, then fades up.
     */
    @Hook(id = "window-show-a", target = bitwig.obf.duG.class, method = "fuN()V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.windowing_system.TopLevelWindowHandle.class, call = "cfM(Z)V")
    public static void windowShowA(com.bitwig.windowing_system.TopLevelWindowHandle handle, boolean visible) {
        beforeShow(handle, visible);
        handle.cfM(visible);
    }

    @Hook(id = "window-show-b", target = bitwig.obf.duG.class, method = "EUG()V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.windowing_system.TopLevelWindowHandle.class, call = "cfM(Z)V")
    public static void windowShowB(com.bitwig.windowing_system.TopLevelWindowHandle handle, boolean visible) {
        beforeShow(handle, visible);
        handle.cfM(visible);
    }

    private static void beforeShow(Object handle, boolean visible) {
        if (!visible || !fadePopups || !MAC || !(handle instanceof com.bitwig.windowing_system.PopupWindowHandle popup)) return;
        try {
            long window = popup.Hnt(); // its native window
            if (window == 0 || !NativeAlpha.set(window, 0)) return;
            fading.put(window, System.nanoTime());
            if (fadeLogs < 3) { fadeLogs++; MOD.log("popup fading in (" + NativeAlpha.classOf(window) + ")"); }
            startFadeTicker();
        } catch (Throwable t) { log("popup fade failed", t); }
    }

    private static java.util.TimerTask fadeTicker;
    private static synchronized void startFadeTicker() {
        if (fadeTicker != null) return;
        try {
            fadeTicker = bitwig.obf.dpN.TVZ().cfM(Minimal::fadeStep, 0, 16);
        } catch (Throwable t) {
            log("fade timer unavailable", t);
            for (Long w : fading.keySet()) NativeAlpha.set(w, 1);
            fading.clear();
        }
    }

    private static void fadeStep() {
        long now = System.nanoTime();
        for (java.util.Map.Entry<Long, Long> e : fading.entrySet()) {
            double t = Math.min(1, (now - e.getValue()) / 1e6 / FADE_MS);
            double a = 1 - Math.pow(1 - t, 3); // ease out
            NativeAlpha.set(e.getKey(), t >= 1 ? 1 : a);
            if (t >= 1) fading.remove(e.getKey());
        }
        if (fading.isEmpty()) synchronized (Minimal.class) { if (fadeTicker != null) { fadeTicker.cancel(); fadeTicker = null; } }
    }


    // ================================================================ from Pill (buttons, toolbar)
    /**
     * Interface: Pill Buttons.
     *
     * Bitwig draws the rounded frames of buttons, fields and panels with a frame shape (dyK and its subclasses dyO, dyQ,
     * dyS). Each asks the graphics for a rounded rectangle, g.xxx(x, y, w, h, radius, corners, ...), to fill it, clip to
     * it or stroke it. These hooks replace those calls: for a frame no taller than the setting, the radius becomes half its
     * height (half its width if narrower), which draws a pill. Corners Bitwig leaves square (the inner ends of joined,
     * segmented buttons) stay square, so a segmented group becomes one pill.
     */



    static volatile boolean on = STORE.getBoolean("enabled", true);
    static volatile double maxH = STORE.getInt("max_height", 32);
    /** How round: 100 = a full pill (radius half the height), less = softer corners. */
    static volatile double roundness = STORE.getInt("roundness", 80);
    private static int pillLogs;

    /** The radius to draw with: a pill for small frames, Bitwig's own otherwise. */
    static double r(SYo g, double w, double h, double r) {
        if (!on || w <= 0 || h <= 0) return r;
        double scale = 1;
        try { scale = g.Hnt(); } catch (Throwable ignored) {}
        if (pillLogs < 8) { pillLogs++; MOD.log("frame " + Math.round(w) + "x" + Math.round(h) + " radius " + r + " scale " + scale); }
        if (h > maxH * Math.max(1, scale) + 0.5) return r; // a panel, not a button
        return Math.max(r, Math.min(w, h) / 2 * Math.max(0, Math.min(100, roundness)) / 100);
    }

    // ---- dyK: fill, clip and border paths of every rounded frame ----

    @Hook(id = "pill-fill", target = bitwig.obf.dyK.class, method = "cfM(Lcom/bitwig/graphics/SYo;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "cfM(DDDDDLjava/util/EnumSet;)V")
    public static void fill(SYo g, double x, double y, double w, double h, double r, EnumSet<?> c) {
        g.cfM(x, y, w, h, r(g, w, h, r), c);
    }

    @Hook(id = "pill-path", target = bitwig.obf.dyK.class, method = "MRY(Lcom/bitwig/graphics/SYo;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "MRY(DDDDDLjava/util/EnumSet;)V")
    public static void path(SYo g, double x, double y, double w, double h, double r, EnumSet<?> c) {
        g.MRY(x, y, w, h, r(g, w, h, r), c);
    }

    @Hook(id = "pill-frame", target = bitwig.obf.dyK.class,
          method = "cfM(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "Hnt(DDDDDLjava/util/EnumSet;)V")
    public static void frame(SYo g, double x, double y, double w, double h, double r, EnumSet<?> c) {
        g.Hnt(x, y, w, h, r(g, w, h, r), c);
    }

    // ---- the toolbar: its buttons draw no outlines ----

    static volatile boolean toolbarPlain = STORE.getBoolean("toolbar_no_outlines", true);
    /** The window toolbars (FILE, PLAY, transport...), as Bitwig builds them; weakly held. */
    private static final java.util.Set<Object> toolbars =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    /** The widget whose outline is being drawn (set on entry to the outline methods below). */
    private static final ThreadLocal<Object> drawing = new ThreadLocal<>();

    @Hook(id = "note-toolbar", target = bitwig.obf.aCf.class, method = "cfM(LaBF;)Ldve;", at = At.WRAP_RETURN)
    public static Object noteToolbar(@bml.api.Return Object toolbar) {
        if (toolbar != null) { toolbars.add(toolbar); MOD.log("toolbar noted: " + toolbar.getClass().getName()); }
        return toolbar;
    }

    @Hook(id = "outline-widget-o", target = bitwig.obf.dyO.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V", at = At.ENTRY)
    public static void outlineWidgetO(@bml.api.Arg(1) Object widget) { drawing.set(widget); }

    @Hook(id = "outline-widget-q", target = bitwig.obf.dyQ.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V", at = At.ENTRY)
    public static void outlineWidgetQ(@bml.api.Arg(1) Object widget) { drawing.set(widget); }

    @Hook(id = "outline-widget-s", target = bitwig.obf.dyS.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V", at = At.ENTRY)
    public static void outlineWidgetS(@bml.api.Arg(1) Object widget) { drawing.set(widget); }

    private static int toolbarLogs;
    /** The top bars: for each noted toolbar piece, its highest ancestor that is still about toolbar height (the bar
     *  that also holds FILE and the transport buttons). Found once the piece is on screen; weakly held. */
    private static final java.util.Set<Object> bars =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static final java.util.Set<Object> resolvedBars =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private static void resolveBars() {
        Object[] pieces;
        synchronized (toolbars) { pieces = toolbars.toArray(); }
        for (Object o : pieces) {
            if (resolvedBars.contains(o) || !(o instanceof bitwig.obf.dve piece)) continue;
            try {
                double ph = piece.yiX();
                if (ph <= 0 || piece.RbC() == null) continue; // not laid out / attached yet
                bitwig.obf.dve best = piece, w = piece.RbC();
                int depth = 0;
                for (; w != null && depth < 30; w = w.RbC(), depth++)
                    if (w.yiX() <= ph * 1.8 + 2 && w.Sxo() >= best.Sxo()) best = w;
                bars.add(best);
                resolvedBars.add(o);
                MOD.log("top bar: " + best.getClass().getName() + " " + best.Sxo() + "x" + best.yiX()
                        + " (from " + piece.getClass().getName() + " " + piece.Sxo() + "x" + ph + ")");
            } catch (Throwable t) { if (toolbarLogs < 3) { toolbarLogs++; MOD.log("finding the top bar failed", t); } }
        }
    }

    /** True when the outline being drawn belongs to a widget in a top bar (and the setting is on). */
    static boolean inToolbar() {
        if (!toolbarPlain || toolbars.isEmpty()) return false;
        if (resolvedBars.size() < toolbars.size()) resolveBars();
        Object w = drawing.get();
        try {
            for (int i = 0; w instanceof bitwig.obf.dve v && i < 40; i++) {
                if (bars.contains(v) || toolbars.contains(v)) {
                    if (toolbarLogs < 3) { toolbarLogs++; MOD.log("toolbar outline skipped: " + drawing.get().getClass().getName()); }
                    return true;
                }
                w = v.RbC(); // its parent
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // ---- dyO, dyQ, dyS: the outlines ----

    @Hook(id = "pill-outline-o", target = bitwig.obf.dyO.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "cfM(DDDDDLiok;Liok;Ljava/util/EnumSet;)V")
    public static void outlineO(SYo g, double x, double y, double w, double h, double r, iok a, iok b, EnumSet<?> c) {
        if (inToolbar()) return;
        g.cfM(x, y, w, h, r(g, w, h, r), a, b, c);
    }

    @Hook(id = "pill-line-o", target = bitwig.obf.dyO.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "cfM(DDDDDLjava/util/EnumSet;D)V")
    public static void lineO(SYo g, double x, double y, double w, double h, double r, EnumSet<?> c, double width) {
        if (inToolbar()) return;
        g.cfM(x, y, w, h, r(g, w, h, r), c, width);
    }

    @Hook(id = "pill-path-o", target = bitwig.obf.dyO.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "MRY(DDDDDLjava/util/EnumSet;)V")
    public static void pathO(SYo g, double x, double y, double w, double h, double r, EnumSet<?> c) {
        g.MRY(x, y, w, h, r(g, w, h, r), c);
    }

    @Hook(id = "pill-outline-q", target = bitwig.obf.dyQ.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "cfM(DDDDDLiok;Liok;Ljava/util/EnumSet;)V")
    public static void outlineQ(SYo g, double x, double y, double w, double h, double r, iok a, iok b, EnumSet<?> c) {
        if (inToolbar()) return;
        g.cfM(x, y, w, h, r(g, w, h, r), a, b, c);
    }

    @Hook(id = "pill-line-s", target = bitwig.obf.dyS.class,
          method = "MRY(Lcom/bitwig/graphics/SYo;Ldve;DDDDLjava/util/EnumSet;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "cfM(DDDDDLjava/util/EnumSet;D)V")
    public static void lineS(SYo g, double x, double y, double w, double h, double r, EnumSet<?> c, double width) {
        if (inToolbar()) return;
        g.cfM(x, y, w, h, r(g, w, h, r), c, width);
    }

    // ---- toolbar icons light up in the highlight colour while the mouse is over their button ----

    static volatile boolean hoverIcons = STORE.getBoolean("toolbar_hover_icons", true);
    /** Widgets the mouse is over now (Bitwig tells each one when the mouse enters and leaves it). */
    private static final java.util.Set<Object> hovered =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static int hoverLogs;

    @Hook(id = "mouse-enter", target = bitwig.obf.drW.class,
          method = "cfM(Ldve;JLeoW;ZLepb;DDDDDDDDZZD)V", at = At.ENTRY)
    public static void mouseEnter(@bml.api.Arg(0) Object widget) {
        if (widget == null) return;
        hovered.add(widget);
        if (hoverIcons) repaintIfToolbar(widget);
    }

    @Hook(id = "mouse-leave", target = bitwig.obf.drW.class,
          method = "MRY(Ldve;JLeoW;ZLepb;DDDDDDDDZZD)V", at = At.ENTRY)
    public static void mouseLeave(@bml.api.Arg(0) Object widget) {
        if (widget == null) return;
        hovered.remove(widget);
        if (hoverIcons) repaintIfToolbar(widget);
    }

    private static void repaintIfToolbar(Object widget) {
        try { if (widget instanceof bitwig.obf.dve v && inToolbar(v)) v.jbi(); } catch (Throwable ignored) {}
    }

    /** True when w sits in a top bar (and not the bar itself). */
    static boolean inToolbar(bitwig.obf.dve w) {
        if (toolbars.isEmpty()) return false;
        if (resolvedBars.size() < toolbars.size()) resolveBars();
        try {
            for (bitwig.obf.dve v = w.RbC(); v != null; v = v.RbC())
                if (bars.contains(v) || toolbars.contains(v)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /** True when the icon is in a toolbar button the mouse is over: the icon or one of its few nearest parents is
     *  hovered, and that one is button-sized (not the whole bar). */
    static boolean hoveredToolbarIcon(bitwig.obf.dve icon) {
        if (!hoverIcons || hovered.isEmpty()) return false;
        try {
            bitwig.obf.dve v = icon;
            for (int i = 0; i < 4 && v != null; i++, v = v.RbC()) {
                if (bars.contains(v) || toolbars.contains(v)) return false;
                if (hovered.contains(v)) return inToolbar(icon);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** The hover colour for this icon: the theme's record red for the record button, else the highlight colour
     *  (Accent (default), which follows Interface: Highlight Colour). */
    private static iok accent(bitwig.obf.dve w) {
        boolean record = false;
        try { for (bitwig.obf.dve v = w; v != null && !record; v = v.RbC()) record = recordButtons.contains(v); } catch (Throwable ignored) {}
        try {
            iok c = w.PEn().cfM(record ? "Record button color" : "Accent (default)").cfM(w);
            if (c != null) return c;
        } catch (Throwable ignored) {}
        return record ? new iok(1f, 0.27f, 0.23f, 1f) : new iok(1f, 0.37f, 0.12f, 1f);
    }

    /** Buttons showing the transport's record icon (their icon is set with dzq.TMF(name), declared on the buttons' base class). */
    private static final java.util.Set<Object> recordButtons =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @Hook(id = "button-icon", target = bitwig.obf.dzq.class, method = "TMF(Ljava/lang/String;)V", at = At.ENTRY)
    public static void buttonIcon(@bml.api.This Object button, @bml.api.Arg(0) String icon) {
        if ("transport_record.svg".equals(icon)) recordButtons.add(button); else recordButtons.remove(button);
        if ("loop.svg".equals(icon)) { loopButtons.add(button); if (loopLogs++ < 2) MOD.log("loop button: " + button.getClass().getName()); }
        else loopButtons.remove(button);
        synchronized (loopButtons) { loopArr = loopButtons.toArray(); }
    }

    /** Buttons showing the transport's loop icon: drawn in mint when the loop is on. */
    private static final java.util.Set<Object> loopButtons =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static int loopLogs;
    private static final java.util.Set<String> loopSeen = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** The colours a toggle is drawn in when it is on. */
    /** Whether the transport loop is on: the loop button's own toggle value (dAE.LIK()). */
    static boolean loopOn() {
        for (Object b : loopArr) {
            try { if (b instanceof bitwig.obf.dAE t && t.LIK() != null && t.LIK().getBoolValue()) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    /*
     * The loop range in the timeline (painted by bpm.cfM): while the loop is on, Bitwig draws its outline in
     * "Accent (hitech)" and its fill as a 40 % mix of it (white and grey in this theme). The painter gets mint instead.
     */
    private static volatile bitwig.obf.eeY loopMint;

    @Hook(id = "loop-range-on", target = bitwig.obf.bpm.class, method = "cfM(LPTe;D)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "hDc()LeeY;")
    public static bitwig.obf.eeY loopRangeOn(bitwig.obf.eff registry) {
        try {
            bitwig.obf.eeY m = loopMint;
            if (m == null) loopMint = m = bitwig.obf.eeY.cfM("Minimal Dark Loop On", MINT);
            if (loopSeen.size() < 24 && loopSeen.add("range painter")) MOD.log("loop range on: drawn mint");
            return m;
        } catch (Throwable t) { return registry.hDc(); }
    }

    // ---- the notifications dot (top right of the window): grey while notifications are hidden, mint while shown ----

    static final iok DOT_GREY = new iok(0x7A / 255f, 0x7A / 255f, 0x7A / 255f, 1f);
    private static volatile bitwig.obf.eeY dotColour;
    private static int dotLogs;

    private static bitwig.obf.eeY dot() {
        bitwig.obf.eeY d = dotColour;
        if (d == null) dotColour = d = bitwig.obf.eeY.cfM("Minimal Dark Notifications Dot", DOT_GREY);
        return d;
    }

    /** dML.gfw() picks the dot's colour: red for an error (kept), else white or the button background: ours instead. */
    @Hook(id = "notification-dot-light", target = bitwig.obf.dML.class, method = "gfw()V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "MYI()LeeY;")
    public static bitwig.obf.eeY notificationDotLight(bitwig.obf.eff r) {
        try { return dot(); } catch (Throwable t) { return r.MYI(); }
    }

    @Hook(id = "notification-dot-plain", target = bitwig.obf.dML.class, method = "gfw()V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.eff.class, call = "BBT()LeeY;")
    public static bitwig.obf.eeY notificationDotPlain(bitwig.obf.eff r) {
        try { return dot(); } catch (Throwable t) { return r.BBT(); }
    }

    private static final java.util.Set<String> LOOP_ON = java.util.Set.of("Accent (hitech)", "Active Toggle Icon (Playing)", "Active Inverted Toggle Icon (Playing)", "Implicit On (subtle)", "Led On");
    static final iok MINT = new iok(0x3B / 255f, 0xE8 / 255f, 0xB0 / 255f, 1f);

    /** Whether this icon widget belongs to a loop button (itself or a few parents up). */
    private static boolean loopIcon(bitwig.obf.dve w) {
        try { for (int i = 0; i < 4 && w != null; i++, w = w.RbC()) if (loopButtons.contains(w)) return true; } catch (Throwable ignored) {}
        return false;
    }

    /** A colour that means "on": clearly coloured, not a grey. */
    private static boolean lit(iok c) {
        float max = Math.max(c.FzB(), Math.max(c.IwV(), c.UNj())), min = Math.min(c.FzB(), Math.min(c.IwV(), c.UNj()));
        return c.hUR() > 0.3f && max - min > 0.2f;
    }

    private static final ThreadLocal<Object> icon = new ThreadLocal<>();

    @Hook(id = "icon-paint", target = bitwig.obf.dqT.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void iconPaint(@bml.api.This Object widget) { icon.set(widget); }

    /** An icon drawn in its own colours (the transport icons): in the highlight colour instead when hovered. */
    @Hook(id = "icon-plain", target = bitwig.obf.dqT.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V",
          at = At.REPLACE_CALL, callOwner = com.bitwig.graphics.BrJ.class, call = "MRY(Lcom/bitwig/graphics/SYo;DD)V")
    public static void iconPlain(com.bitwig.graphics.BrJ image, SYo g, double x, double y) {
        Object w = icon.get();
        if (w instanceof bitwig.obf.dve lv && loopIcon(lv) && loopSeen.size() < 12 && loopSeen.add("plain " + lv.getClass().getName()))
            MOD.log("loop icon drawn in its own colours (" + lv.getClass().getName() + ")");
        if (w instanceof bitwig.obf.dve v && hoveredToolbarIcon(v)) {
            g.qoU();
            try { g.Hnt(accent(v)); image.cfM(g, x, y); } finally { g.ucQ(); }
            if (hoverLogs < 3) { hoverLogs++; MOD.log("toolbar icon hovered: " + v.getClass().getName()); }
            return;
        }
        image.MRY(g, x, y);
    }

    /** An icon drawn in a colour: the highlight colour instead when hovered. */
    @Hook(id = "icon-tinted", target = bitwig.obf.dqT.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V",
          at = At.REPLACE_CALL, callOwner = SYo.class, call = "Hnt(Liok;)V")
    public static void iconTinted(SYo g, iok colour) {
        Object w = icon.get();
        if (w instanceof bitwig.obf.dve v && colour != null && loopIcon(v)) {
            String key = "tinted " + Integer.toHexString(colour.hashCode());
            if (loopSeen.size() < 12 && loopSeen.add(key)) MOD.log("loop icon tinted " + Math.round(colour.FzB() * 255) + "," + Math.round(colour.IwV() * 255) + "," + Math.round(colour.UNj() * 255) + (lit(colour) ? " (on: mint)" : ""));
        }
        if (w instanceof bitwig.obf.dve v && hoveredToolbarIcon(v)) { g.Hnt(accent(v)); return; }
        g.Hnt(colour);
    }

    // ---- the transport toolbar at the bottom of the window ----

    /*
     * The window's column is built in aBW.G_(): the toolbar (dWb.HL, call 1), then the main area and the bottom bar
     * (call 4), or in one layout a single area (call 2) instead of calls 3 and 4. With the setting on, the toolbar is
     * held back at call 1 and added after whichever of calls 2 or 4 runs, so it sits at the bottom. Applies when a
     * window is built (restart Bitwig).
     */
    static volatile boolean transportBottom = STORE.getBoolean("transport_bottom", false);
    private static final ThreadLocal<bitwig.obf.dve> heldToolbar = new ThreadLocal<>();

    @Hook(id = "toolbar-add", target = bitwig.obf.aBW.class, method = "G_()V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dWb.class, call = "HL(Ldve;)V", occurrence = 1)
    public static void addToolbar(bitwig.obf.dWb column, bitwig.obf.dve toolbar) {
        if (transportBottom && toolbar != null) { heldToolbar.set(toolbar); MOD.log("transport toolbar moved to the bottom"); return; }
        column.HL(toolbar);
    }

    @Hook(id = "area-add", target = bitwig.obf.aBW.class, method = "G_()V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dWb.class, call = "HL(Ldve;)V", occurrence = 2)
    public static void addArea(bitwig.obf.dWb column, bitwig.obf.dve area) { column.HL(area); addHeld(column); }

    @Hook(id = "bottom-bar-add", target = bitwig.obf.aBW.class, method = "G_()V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dWb.class, call = "HL(Ldve;)V", occurrence = 4)
    public static void addBottomBar(bitwig.obf.dWb column, bitwig.obf.dve bar) {
        if (barTop && bar != null) {
            // under the toolbar (index 1), or first when the toolbar is held back for the bottom
            int at = heldToolbar.get() != null ? 0 : Math.min(1, column.vJT().size());
            try { column.a_(bar, at); MOD.log("ARRANGE/MIX/EDIT bar moved to the top"); }
            catch (Throwable t) { MOD.log("moving the bottom bar failed", t); if (bar.RbC() == null) column.HL(bar); }
        } else column.HL(bar);
        addHeld(column);
    }

    /** The bottom bar (ARRANGE / MIX / EDIT, panels) at the top of the window, under the toolbar. */
    static volatile boolean barTop = STORE.getBoolean("bottom_bar_top", false);

    private static void addHeld(bitwig.obf.dWb column) {
        bitwig.obf.dve t = heldToolbar.get();
        if (t == null) return;
        heldToolbar.remove();
        column.HL(t);
    }

    // ---- sentence case instead of Bitwig's capitals (headers, tabs, section titles, hints): see Labels.caps ----

    @Hook(id = "sentence-case-1", target = bitwig.obf.aBW.class, method = "Scv()Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase1(String s) { return Labels.caps(s, 1); }

    @Hook(id = "sentence-case-2", target = bitwig.obf.asn.class, method = "cfM(Ljava/lang/String;)Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase2(String s) { return Labels.caps(s, 2); }

    @Hook(id = "sentence-case-3", target = bitwig.obf.bDH.class, method = "wGc(Ljava/lang/String;)V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase3(String s) { return Labels.caps(s, 3); }

    @Hook(id = "sentence-case-4", target = bitwig.obf.bnK.class, method = "cfM(Ljava/lang/String;)LdAa;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase4(String s) { return Labels.caps(s, 4); }

    @Hook(id = "sentence-case-5", target = bitwig.obf.cEk.class, method = "cfM(Ljava/lang/String;)Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase5(String s) { return Labels.caps(s, 5); }

    @Hook(id = "sentence-case-6", target = bitwig.obf.cEk.class, method = "cfM(Ljava/lang/String;Ljava/lang/Object;Z)Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase6(String s) { return Labels.caps(s, 6); }

    @Hook(id = "sentence-case-7", target = bitwig.obf.dEG.class, method = "cfM(Ljava/lang/Object;Ljava/util/List;)Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase7(String s) { return Labels.caps(s, 7); }

    @Hook(id = "sentence-case-8", target = bitwig.obf.dKN.class, method = "gfw()V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase8(String s) { return Labels.caps(s, 8); }

    @Hook(id = "sentence-case-9", target = bitwig.obf.dKN.class, method = "MRY(Ljava/lang/String;)V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase9(String s) { return Labels.caps(s, 9); }

    @Hook(id = "sentence-case-10", target = bitwig.obf.drh.class, method = "cfM(Ljava/lang/String;)V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase10(String s) { return Labels.caps(s, 10); }

    @Hook(id = "sentence-case-11", target = bitwig.obf.dnO.class, method = "FzB()Ljava/lang/String;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase11(String s) { return Labels.caps(s, 11); }

    @Hook(id = "sentence-case-12", target = com.bitwig.flt.widget.core.device.ZSx.class, method = "cfM(LdKW;DDLeoW;)V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;", occurrence = 1)
    public static String sentenceCase12(String s) { return Labels.caps(s, 12); }

    @Hook(id = "sentence-case-13", target = com.bitwig.flt.widget.core.device.ZSx.class, method = "cfM(LdKW;DDLeoW;)V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;", occurrence = 2)
    public static String sentenceCase13(String s) { return Labels.caps(s, 13); }

    @Hook(id = "sentence-case-14", target = com.bitwig.flt.widget.core.device.ZSx.class, method = "cfM(LdKW;DDLeoW;)V", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;", occurrence = 3)
    public static String sentenceCase14(String s) { return Labels.caps(s, 14); }

    @Hook(id = "sentence-case-15", target = com.bitwig.flt.widget.core.online.oRV.class, method = "cfM(LYc;Lcom/bitwig/base/geom/LWw;Z)Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;")
    public static String sentenceCase15(String s) { return Labels.caps(s, 15); }

    @Hook(id = "sentence-case-16", target = bitwig.obf.dre.class, method = "IwV()Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;", occurrence = 1)
    public static String sentenceCase16(String s) { return Labels.caps(s, 16); }

    @Hook(id = "sentence-case-17", target = bitwig.obf.dre.class, method = "IwV()Ldve;", at = At.REPLACE_CALL,
          callOwner = String.class, call = "toUpperCase()Ljava/lang/String;", occurrence = 2)
    public static String sentenceCase17(String s) { return Labels.caps(s, 17); }

    /*
     * Labels (dut) and buttons (dzg) can carry a text transform (a UnaryOperator), and Bitwig passes String::toUpperCase
     * for headers and tabs: applied in dut.hbP(text) and dzg.cfM(transform, text). Where the transform's result is just
     * the text in capitals, sentence case is drawn instead; any other transform is left alone.
     */
    @Hook(id = "sentence-case-label", target = bitwig.obf.dut.class, method = "hbP(Ljava/lang/String;)Ljava/lang/String;",
          at = At.REPLACE_CALL, callOwner = java.util.function.UnaryOperator.class, call = "apply(Ljava/lang/Object;)Ljava/lang/Object;")
    public static Object sentenceCaseLabel(java.util.function.UnaryOperator op, Object text) { return transformed(op, text, 20); }

    @Hook(id = "sentence-case-button", target = bitwig.obf.dzg.class, method = "cfM(Ljava/util/function/UnaryOperator;Ljava/lang/Object;)Ljava/lang/String;",
          at = At.REPLACE_CALL, callOwner = java.util.function.UnaryOperator.class, call = "apply(Ljava/lang/Object;)Ljava/lang/Object;")
    public static Object sentenceCaseButton(java.util.function.UnaryOperator op, Object text) { return transformed(op, text, 21); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object transformed(java.util.function.UnaryOperator op, Object text, int site) {
        Object out = op.apply(text);
        if (text instanceof String t && out instanceof String o && !o.equals(t) && o.equals(t.toUpperCase())) return Labels.caps(t, site);
        return out;
    }

    /** The Dashboard's tabs (Settings, Packages, Help...) are LJt texts turned to capitals with LJt.g_(): sentence case instead. */
    @Hook(id = "sentence-case-tabs", target = bitwig.obf.aBl.class, method = "cfM(LLJt;Ldve;ZZ)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.LJt.class, call = "g_()LLJt;")
    public static bitwig.obf.LJt sentenceCaseTabs(bitwig.obf.LJt text) { return captioned(text, 22); }

    @Hook(id = "sentence-case-captions", target = bitwig.obf.aLZ.class, method = "cfM(LLJt;Ldve;)Ldve;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.LJt.class, call = "g_()LLJt;")
    public static bitwig.obf.LJt sentenceCaseCaptions(bitwig.obf.LJt text) { return captioned(text, 23); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static bitwig.obf.LJt captioned(bitwig.obf.LJt text, int site) {
        try { return text.cfM((java.util.function.Function) (Object o) -> o instanceof String t ? Labels.caps(t, site) : o); }
        catch (Throwable t) { return text.g_(); }
    }

    /** A label measuring its text (dut.iG_, its preferred size): measured as Bitwig's capitals, so sizes don't change. */
    @Hook(id = "sentence-case-measure", target = bitwig.obf.dut.class, method = "iG_()Lcom/bitwig/base/geom/SYo;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.dut.class, call = "hbP(Ljava/lang/String;)Ljava/lang/String;")
    public static String sentenceCaseMeasure(bitwig.obf.dut label, String text) {
        String shown = label.hbP(text);
        String upper = shown == null ? null : Labels.upperOf.get(shown);
        return upper != null ? upper : shown;
    }
}
