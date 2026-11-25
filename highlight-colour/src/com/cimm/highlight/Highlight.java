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

    /** Recolours the accent family to the current choice (or back to the originals). */
    static void apply() {
        List<Object[]> todo = new ArrayList<>();
        synchronized (originals) { for (var e : originals.entrySet()) todo.add(new Object[]{e.getKey(), e.getValue(), names.get(e.getKey())}); }
        if (todo.isEmpty()) MOD.log("no highlight colours to change yet");
        if (todo.isEmpty()) return;
        iok main = null;
        for (Object[] t : todo) if (MAIN.equals(t[2])) main = (iok) t[1];
        int choice = choice();
        Color target = choice == CUSTOM ? Color.getHSBColor(hue() / 360f, 0.85f, 1f) : choice == 0 ? null : new Color(RGB[choice]);
        int changed = 0;
        for (Object[] t : todo) {
            iok original = (iok) t[1];
            iok next = target == null || main == null ? original : recolour(original, main, target, MAIN.equals(t[2]));
            if (next == null) continue;
            try { ((bitwig.obf.eeY) t[0]).MRY(next); changed++; }
            catch (Throwable e) { MOD.log("setting " + t[2] + " failed", e); }
        }
        MOD.log("highlight " + LABELS[choice] + (choice == CUSTOM ? " " + hue() + "°" : "") + ": " + changed + " colours");
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

    private static int c(float v) { return Math.round(Math.max(0, Math.min(1, v)) * 255); }
}
