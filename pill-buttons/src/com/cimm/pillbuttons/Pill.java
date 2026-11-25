package com.cimm.pillbuttons;

import bitwig.obf.iok; // a colour
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.graphics.SYo; // the graphics a widget paints with

import java.util.EnumSet;

/**
 * Interface: Pill Buttons.
 *
 * Bitwig draws the rounded frames of buttons, fields and panels with a frame shape (dyK and its subclasses dyO, dyQ,
 * dyS). Each asks the graphics for a rounded rectangle, g.xxx(x, y, w, h, radius, corners, ...), to fill it, clip to
 * it or stroke it. These hooks replace those calls: for a frame no taller than the setting, the radius becomes half its
 * height (half its width if narrower), which draws a pill. Corners Bitwig leaves square (the inner ends of joined,
 * segmented buttons) stay square, so a segmented group becomes one pill.
 */
public final class Pill {
    private Pill() {}

    static final Bml.Mod MOD = Bml.mod("pill-buttons");
    static final bml.api.Settings STORE = MOD.settings();

    static volatile boolean on = STORE.getBoolean("enabled", true);
    static volatile double maxH = STORE.getInt("max_height", 32);
    private static int logs;

    /** The radius to draw with: a pill for small frames, Bitwig's own otherwise. */
    static double r(SYo g, double w, double h, double r) {
        if (!on || w <= 0 || h <= 0) return r;
        double scale = 1;
        try { scale = g.Hnt(); } catch (Throwable ignored) {}
        if (logs < 8) { logs++; MOD.log("frame " + Math.round(w) + "x" + Math.round(h) + " radius " + r + " scale " + scale); }
        if (h > maxH * Math.max(1, scale) + 0.5) return r; // a panel, not a button
        return Math.min(w, h) / 2;
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
    /** True when the outline being drawn belongs to a widget in a toolbar (and the setting is on). */
    static boolean inToolbar() {
        if (!toolbarPlain || toolbars.isEmpty()) return false;
        Object w = drawing.get();
        try {
            for (int i = 0; w instanceof bitwig.obf.dve v && i < 40; i++) {
                if (toolbars.contains(v)) {
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
}
