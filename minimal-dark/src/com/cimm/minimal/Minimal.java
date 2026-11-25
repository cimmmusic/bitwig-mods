package com.cimm.minimal;

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
        value = Theme.relative(colour, value);
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
                    if (w instanceof bitwig.obf.dzq b && b.yiX() <= 80 && b.Sxo() <= 400) return b;
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

    /** A window about to be shown or hidden (CocoaWindowHandle.Hnt(visible)): a popup goes see-through, then fades up. */
    @Hook(id = "window-showing", target = com.bitwig.mac_windowing_system.CocoaWindowHandle.class, method = "Hnt(Z)V", at = At.ENTRY)
    public static void windowShowing(@bml.api.This Object handle, @bml.api.Arg(0) boolean visible) {
        if (!visible || !fadePopups || !(handle instanceof com.bitwig.windowing_system.PopupWindowHandle popup)) return;
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

}
