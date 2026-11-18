package com.cimm.pinchannels;

import bitwig.obf.buQ; // a per-track row widget (arranger header row, clip launcher row, lane row…): ELS() = its track
import bitwig.obf.dTA; // a vertically scrolling view (each arranger column has one: headers, launcher, timeline)
import bitwig.obf.dve; // a widget
import bitwig.obf.iok; // a colour
import bitwig.obf.xJo; // a track
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Frozen rows, like Excel's Freeze Panes: a pinned track's row stays in its place in the list, and once you scroll
 * past it, it sticks to the top edge while the other tracks slide under it.
 *
 * Every arranger column scrolls in its own dTA view, and every track is one row widget (buQ, ELS() = its track) at the
 * same height in each column. After a dTA view paints, the rows of pinned tracks that have scrolled above its top edge
 * are painted again at the top, stacked in list order, with a thin shadow line under them.
 */
public final class Sticky {
    private Sticky() {}

    static final iok BG = iok.cfM(42, 42, 44);
    static final iok SHADOW = iok.cfM(14, 14, 15);
    /** Bitwig's rows: 86 tall, 2 apart (every 88), with a dark separator in the gap. Frozen rows keep that spacing so
     *  a row doesn't jump when it freezes, and keep the separator between them. */
    static final double GAP = 2;

    /** Per view: its pinned-track rows (refreshed every 300 ms, so painting stays cheap). */
    private static final Map<dTA, Rows> cache = new WeakHashMap<>();
    private static final class Rows { long at; int version = -1; List<buQ> rows = List.of(); double lastTop = Double.NaN; boolean frozen; }
    private static boolean logged, loggedFail;
    private static final java.util.Set<String> seen = new java.util.HashSet<>();

    @Hook(id = "frozen-rows", target = dTA.class, method = "Hnt(Lcom/bitwig/graphics/SYo;)V", at = At.OVERRIDE_AFTER_SUPER)
    public static void paint(@This dTA view, @Arg(0) com.bitwig.graphics.SYo g) {
        if (!Pins.any()) { clear(view); return; }
        try {
            List<buQ> rows = rows(view);
            if (rows.isEmpty()) { clear(view); return; }
            com.bitwig.base.geom.OLR v = view.ihs();
            if (v == null) return;
            double vx = v.FzB(), vy = v.hUR(), vw = view.Sxo();
            // Scrolling only redraws the newly shown strip and shifts the rest (frozen rows included), so when the rows
            // have moved since the last paint, ask for one full repaint of the view.
            Rows c = cache.get(view);
            double firstTop = rows.get(0).ihs() == null ? Double.NaN : rows.get(0).ihs().hUR() - vy;
            boolean moved = c != null && firstTop != c.lastTop;
            if (c != null) c.lastTop = firstTop;
            // A track's row can be several pieces side by side (the clip launcher: a 50-wide strip, the slots, another
            // strip), so pieces of the same track share one frozen slot; the next track goes under the tallest piece.
            java.util.LinkedHashMap<Object, List<buQ>> byTrack = new java.util.LinkedHashMap<>();
            for (buQ row : rows) {
                Object t;
                try { t = row.ELS(); } catch (Throwable e) { t = row; }
                byTrack.computeIfAbsent(t, k -> new ArrayList<>()).add(row);
            }
            double stack = 0;
            List<Slot> slots = new ArrayList<>();
            for (List<buQ> pieces : byTrack.values()) {
                double top = Double.MAX_VALUE, h = 0;
                for (buQ p : pieces) {
                    com.bitwig.base.geom.OLR r = p.ihs();
                    if (r == null) continue;
                    top = Math.min(top, r.hUR() - vy);
                    h = Math.max(h, p.yiX());
                }
                if (h <= 0 || top >= stack) { stack = Math.max(stack, 0); continue; } // still in view where it is
                g.qoU();
                try {
                    g.MRY(0, stack, vw, h); g.lYc();            // clip to the frozen slot
                    g.Hnt(BG); g.MRY(0, stack, vw, h); g.wGc(); // cover what scrolled under it
                } finally { g.ucQ(); }
                for (buQ p : pieces) {
                    com.bitwig.base.geom.OLR r = p.ihs();
                    if (r == null) continue;
                    double x = r.FzB() - vx, dy = (r.hUR() - vy) - top; // keep each piece's place within the row
                    slots.add(new Slot(p, x, stack + dy, p.Sxo(), p.yiX(), stack, h));
                    g.qoU();
                    try {
                        g.MRY(0, stack, vw, h); g.lYc();
                        g.FzB(x, stack + dy);
                        p.Hnt(g);
                    } finally { g.ucQ(); }
                }
                g.Hnt(SHADOW); g.MRY(0, stack + h, vw, GAP); g.wGc(); // the separator under it, as between Bitwig's rows
                stack += h + GAP;
            }
            boolean frozenNow = stack > 0;
            clear(view);
            if (!slots.isEmpty()) frozenSlots.put(view, slots);
            for (Slot sl : slots) frozenRows.put(sl.row, view);
            if (c != null && (moved && (frozenNow || c.frozen))) view.jbi(); // full repaint next frame
            if (c != null) c.frozen = frozenNow;
            if (frozenNow && seen.add(view.getClass().getSimpleName() + ":" + rows.get(0).getClass().getSimpleName() + ":" + (long) vw))
                Pins.MOD.log("frozen in " + (long) vw + "-wide view: rows " + rows.stream().map(r -> r.getClass().getSimpleName() + " " + (long) r.Sxo() + "x" + (long) r.yiX()).toList());
            if (!logged && stack > 0) { logged = true; Pins.MOD.log("frozen rows: " + rows.size() + " pinned row(s) in a " + view.getClass().getSimpleName() + " " + (long) vw + "x" + (long) view.yiX()); }
        } catch (Throwable t) {
            if (!loggedFail) { loggedFail = true; Pins.MOD.log("frozen rows failed", t); }
        }
    }

    /** A piece of a frozen row as drawn: where (view coordinates) and the slot it sits in. */
    private record Slot(buQ row, double x, double y, double w, double h, double slotTop, double slotH) {}
    /** Per view: what is frozen at its top right now (from its last paint). UI thread only. */
    private static final Map<dTA, List<Slot>> frozenSlots = new WeakHashMap<>();
    /** Frozen row → its view, for repaints. */
    private static final Map<dve, dTA> frozenRows = new WeakHashMap<>();

    private static void clear(dTA view) {
        List<Slot> old = frozenSlots.remove(view);
        if (old != null) for (Slot sl : old) frozenRows.remove(sl.row);
    }

    // ---- clicks: a press on a frozen slot goes to the real (scrolled-away) row ----
    private record Press(dve view, bitwig.obf.eoX button, double x, double y, bitwig.obf.eoW mods, int clicks) {}
    private static final ThreadLocal<Press> press = new ThreadLocal<>();

    /** Every press first reaches a container's capture handler (dtM.cfM); note where it landed for a view. */
    @Hook(id = "frozen-press-at", target = dve.class, method = "cfM(LeoX;DDLeoW;I)LdrX;", at = At.ENTRY)
    public static void pressAt(@This dve w, @Arg(0) bitwig.obf.eoX button, @Arg(1) double x, @Arg(2) double y,
                               @Arg(3) bitwig.obf.eoW mods, @Arg(4) int clicks) {
        if (w instanceof dTA view && frozenSlots.containsKey(view)) {
            press.set(new Press(view, button, x, y, mods, clicks));
            try { lastButton = button.name(); } catch (Throwable ignored) {}
        }
    }

    /** …and if it is inside a frozen slot, the press goes to the frozen row's real widget, in its own coordinates. */
    @Hook(id = "frozen-press", target = dve.class, method = "cfM(LeoX;DDLeoW;I)LdrX;", at = At.WRAP_RETURN)
    public static bitwig.obf.drX pressed(@Return bitwig.obf.drX result, @This dve w) {
        Press p = press.get();
        if (p == null || p.view != w) return result;
        press.remove();
        try {
            List<Slot> slots = frozenSlots.get((dTA) w);
            if (slots == null) return result;
            boolean inSlot = false;
            for (Slot sl : slots) {
                if (p.y < sl.slotTop || p.y >= sl.slotTop + sl.slotH) continue;
                inSlot = true;
                if (p.x >= sl.x && p.x < sl.x + sl.w && p.y >= sl.y && p.y < sl.y + sl.h) {
                    bitwig.obf.drX d = deliver(sl.row, p.x - sl.x, p.y - sl.y, p);
                    if (d != null) return d;
                    if ("SELECTION".equals(p.button.name())) return new Select(sl.row, (dTA) w); // nothing took it: select the track
                    return null;
                }
            }
            if (inSlot) return null; // on a frozen slot but nothing there: not the hidden row underneath
        } catch (Throwable t) { if (!loggedFail) { loggedFail = true; Pins.MOD.log("frozen row click failed", t); } }
        return result;
    }
    private static boolean loggedClick;

    /**
     * Bitwig routes a press itself (the widget under the pointer, its parents…), so a row handed a press does nothing
     * with it. Here: the deepest widget of the row under (x, y) and its parents up to the row; capture handlers
     * top-down, then bubble handlers bottom-up, each in its own coordinates (absolute positions minus the row's).
     */
    private static bitwig.obf.drX deliver(buQ row, double x, double y, Press p) {
        com.bitwig.base.geom.OLR r0 = row.ihs();
        if (r0 == null) return null;
        double ax = r0.FzB() + x, ay = r0.hUR() + y;              // the point where the row really is
        dve deepest = row; int best = 0;
        for (Object o : row.lYc(dve.class).toList()) {
            dve d = (dve) o;
            com.bitwig.base.geom.OLR r = d.ihs();
            if (r == null || d.Sxo() <= 0 || d.yiX() <= 0) continue;
            if (ax < r.FzB() || ay < r.hUR() || ax >= r.FzB() + d.Sxo() || ay >= r.hUR() + d.yiX()) continue;
            int depth = 0;
            for (dve q = d; q != null && q != row; q = q.RbC()) depth++;
            if (depth >= best) { best = depth; deepest = d; }
        }
        List<dve> chain = new ArrayList<>();
        for (dve q = deepest; q != null; q = q.RbC()) { chain.add(0, q); if (q == row) break; }
        String log = loggedClick ? null : "frozen row click -> " + deepest.getClass().getSimpleName() + " in " + row.getClass().getSimpleName();
        for (dve q : chain) {                                    // capture: parents first
            com.bitwig.base.geom.OLR r = q.ihs();
            bitwig.obf.drX d = q.cfM(p.button, ax - r.FzB(), ay - r.hUR(), p.mods, p.clicks);
            if (d != null) { logClick(log, q, "capture"); return d; }
        }
        for (int i = chain.size() - 1; i >= 0; i--) {           // bubble: the widget itself first
            dve q = chain.get(i);
            com.bitwig.base.geom.OLR r = q.ihs();
            bitwig.obf.drX d = q.MRY(p.button, ax - r.FzB(), ay - r.hUR(), p.mods, p.clicks);
            if (d != null) { logClick(log, q, "bubble"); return d; }
        }
        logClick(log, null, "no handler: selects the track");
        return null;
    }

    private static void logClick(String log, dve by, String how) {
        if (log == null || !"SELECTION".equals(lastButton)) return;
        loggedClick = true;
        Pins.MOD.log(log + " -> " + how + (by == null ? "" : " by " + by.getClass().getSimpleName()));
    }
    private static String lastButton = "";

    /** A press on an empty part of a frozen row: when the click really begins, select that track (as clicking its name). */
    static final class Select extends bitwig.obf.drX {
        final buQ row; final dTA view;
        Select(buQ row, dTA view) { this.row = row; this.view = view; }
        @Override public void cfM(bitwig.obf.dJH e) {
            super.cfM(e);
            try {
                Object track = row.ELS();
                dve panel = view;
                while (panel != null && !(panel instanceof bitwig.obf.aYp)) panel = panel.RbC();
                if (panel instanceof bitwig.obf.aYp arranger && track instanceof com.bitwig.ramona.core.OLR o) {
                    arranger.BBT().UNj(o);
                    view.jbi();
                } else Pins.MOD.log("frozen row select: no arranger panel / track");
            } catch (Throwable t) { Pins.MOD.log("frozen row select failed", t); }
        }
    }

    // ---- repaints: a widget inside a frozen row redrawing itself would paint over the copy at the top ----
    @Hook(id = "frozen-repaint", target = dve.class, method = "jbi()V", at = At.ENTRY)
    public static void repaint(@This dve w) {
        if (frozenRows.isEmpty()) return;
        int depth = 0;
        for (dve p = w; p != null && depth < 16; p = p.RbC(), depth++) {
            dTA view = frozenRows.get(p);
            if (view != null) { if (view != w) view.jbi(); return; }
        }
    }

    /** The view's rows of pinned tracks, outermost row per track (rows nest: a header row holds a header), top first. */
    private static List<buQ> rows(dTA view) {
        Rows c = cache.computeIfAbsent(view, k -> new Rows());
        long now = System.currentTimeMillis();
        if (c.version == Pins.version && now - c.at < 300) return c.rows;
        c.at = now; c.version = Pins.version;
        List<buQ> out = new ArrayList<>();
        for (Object o : view.lYc(buQ.class).toList()) {
            buQ row = (buQ) o;
            xJo t;
            try { t = row.ELS() instanceof xJo x ? x : null; } catch (Throwable e) { t = null; }
            if (t == null || !Pins.isPinned(t)) continue;
            boolean nested = false;
            for (dve p = row.RbC(); p != null && p != view; p = p.RbC()) if (p instanceof buQ) { nested = true; break; }
            if (!nested) out.add(row);
        }
        out.sort((a, b) -> {
            try { return Long.compare(a.ihs().hUR(), b.ihs().hUR()); } catch (Throwable e) { return 0; }
        });
        c.rows = out;
        return out;
    }
}
