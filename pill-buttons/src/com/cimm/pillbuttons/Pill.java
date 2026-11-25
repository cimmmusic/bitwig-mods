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
    /** How round: 100 = a full pill (radius half the height), less = softer corners. */
    static volatile double roundness = STORE.getInt("roundness", 80);
    private static int logs;

    /** The radius to draw with: a pill for small frames, Bitwig's own otherwise. */
    static double r(SYo g, double w, double h, double r) {
        if (!on || w <= 0 || h <= 0) return r;
        double scale = 1;
        try { scale = g.Hnt(); } catch (Throwable ignored) {}
        if (logs < 8) { logs++; MOD.log("frame " + Math.round(w) + "x" + Math.round(h) + " radius " + r + " scale " + scale); }
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
    private static final java.util.Set<Object> resolved =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private static void resolveBars() {
        Object[] pieces;
        synchronized (toolbars) { pieces = toolbars.toArray(); }
        for (Object o : pieces) {
            if (resolved.contains(o) || !(o instanceof bitwig.obf.dve piece)) continue;
            try {
                double ph = piece.yiX();
                if (ph <= 0 || piece.RbC() == null) continue; // not laid out / attached yet
                bitwig.obf.dve best = piece, w = piece.RbC();
                int depth = 0;
                for (; w != null && depth < 30; w = w.RbC(), depth++)
                    if (w.yiX() <= ph * 1.8 + 2 && w.Sxo() >= best.Sxo()) best = w;
                bars.add(best);
                resolved.add(o);
                MOD.log("top bar: " + best.getClass().getName() + " " + best.Sxo() + "x" + best.yiX()
                        + " (from " + piece.getClass().getName() + " " + piece.Sxo() + "x" + ph + ")");
            } catch (Throwable t) { if (toolbarLogs < 3) { toolbarLogs++; MOD.log("finding the top bar failed", t); } }
        }
    }

    /** True when the outline being drawn belongs to a widget in a top bar (and the setting is on). */
    static boolean inToolbar() {
        if (!toolbarPlain || toolbars.isEmpty()) return false;
        if (resolved.size() < toolbars.size()) resolveBars();
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
        if (resolved.size() < toolbars.size()) resolveBars();
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
    }

    private static final ThreadLocal<Object> icon = new ThreadLocal<>();

    @Hook(id = "icon-paint", target = bitwig.obf.dqT.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void iconPaint(@bml.api.This Object widget) { icon.set(widget); }

    /** An icon drawn in its own colours (the transport icons): in the highlight colour instead when hovered. */
    @Hook(id = "icon-plain", target = bitwig.obf.dqT.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V",
          at = At.REPLACE_CALL, callOwner = com.bitwig.graphics.BrJ.class, call = "MRY(Lcom/bitwig/graphics/SYo;DD)V")
    public static void iconPlain(com.bitwig.graphics.BrJ image, SYo g, double x, double y) {
        Object w = icon.get();
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
    public static void addBottomBar(bitwig.obf.dWb column, bitwig.obf.dve bar) { column.HL(bar); addHeld(column); }

    private static void addHeld(bitwig.obf.dWb column) {
        bitwig.obf.dve t = heldToolbar.get();
        if (t == null) return;
        heldToolbar.remove();
        column.HL(t);
    }
}
