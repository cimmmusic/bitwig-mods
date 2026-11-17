package com.cimm.mixerminipanels;

import bitwig.obf.Ixm; // size policy: fixed at the widget's preferred size
import bitwig.obf.NBQ; // a device's remote controls (its pages)
import bitwig.obf.kn; // size policy: the widget's natural min/preferred/max size
import bitwig.obf.w3; // Bitwig's standard fixed sizes; cfM is the 17-unit row height
import bitwig.obf.dWb; // a vertical or horizontal group of widgets
import bitwig.obf.dve; // a widget
import bitwig.obf.dyC; // something that can be a widget's content
import bitwig.obf.jsd; // a device
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.widget.core.device.g2L; // one device's row in the mixer's Devices section
import com.bitwig.flt.widget.core.device.uMi; // the mixer's Devices section (its device chain)

/**
 * Mixer: Device Mini Panels.
 *
 * In the mixer's Devices section every device is a one-line row (on/off, name, window button).
 * This puts the device's current remote controls page under its row, as Bitwig's own mixer
 * remote-control knobs, so EQ+, Compressor and the other native devices can be tweaked
 * from the mixer channel.
 */
public final class MixerMiniPanels {
    private static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    private static volatile boolean loggedFailure;
    private static final java.util.concurrent.atomic.AtomicInteger diag = new java.util.concurrent.atomic.AtomicInteger();

    private MixerMiniPanels() {}

    /**
     * Bitwig builds the row and then sets it as the row widget's content; we set a column instead:
     * Bitwig's row on top, the device's remote controls underneath.
     */
    /**
     * The mixer builds each device row with g2L.cfM(device, flag, showPanel); showPanel adds Bitwig's own small device
     * overview (e.g. EQ+'s) into the row. For the devices that get our mini view, build the row without it.
     */
    @Hook(id = "no-bitwig-overview", target = uMi.class, method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;",
          at = At.REPLACE_CALL, callOwner = g2L.class, call = "cfM(Ljsd;ZZ)Lcom/bitwig/flt/widget/core/device/g2L;")
    public static g2L buildRow(jsd device, boolean flag, boolean showPanel) {
        boolean ours = false;
        try { ours = device != null && MOD.settings().getBoolean("enabled", true) && Kinds.supported(device); } catch (Throwable ignored) {}
        boolean keep = showPanel;
        if (ours && showPanel) {
            // Bitwig's extra is either an overview panel under the row (R1Z, e.g. EQ+'s curve: ours replaces it) or a
            // compact strip in the row itself (zcJ, e.g. the compressors' gain-reduction meter: kept beside our view).
            Object extra = null;
            try { extra = device.OzW(); } catch (Throwable ignored) {}
            keep = !(extra instanceof bitwig.obf.R1Z);
            if (MiniGraph.DEBUG && overviewLog.add(Params.safeName(device)))
                if (MiniGraph.DEBUG) MOD.log("row extra for " + Params.safeName(device) + ": " + (extra == null ? "none" : extra.getClass().getName())
                        + (extra instanceof bitwig.obf.R1Z ? " (overview panel: hidden)" : extra instanceof bitwig.obf.zcJ ? " (in-row meter: kept)" : " (kept)"));
        }
        ROW_IN_INSPECTOR.set(flag); // Bitwig sets this flag for the track Inspector's rows (it shows their window button)
        try { return g2L.cfM(device, flag, keep); } // Bitwig's row builder (setRowContent runs inside it)
        finally { ROW_IN_INSPECTOR.remove(); }
    }
    /** While a row is being built: whether it is in the track Inspector (else the mixer). */
    private static final ThreadLocal<Boolean> ROW_IN_INSPECTOR = new ThreadLocal<>();
    private static final java.util.Set<String> placeLog = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static int place() {
        Boolean f = ROW_IN_INSPECTOR.get();
        return Boolean.TRUE.equals(f) ? Collapse.INSPECTOR : Collapse.MIXER;
    }
    private static final java.util.Set<String> overviewLog = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Wheel / trackpad events where Bitwig receives them (dpl.cfM(eoy) hands each kind to its handler): a scroll over a
     * node changes its Q and Bitwig doesn't see it; otherwise Bitwig's handler runs as usual. eph is the wheel/trackpad
     * scroll; epr/eps/epv/epz are pen/touch gestures (passed through the same check).
     */
    @Hook(id = "scroll-epr", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "MRY(Lepr;)V", occurrence = 1)
    public static void scroll_epr(bitwig.obf.dpl input, bitwig.obf.epr e) { if (!Wheel.consume(e)) input.MRY(e); }

    @Hook(id = "scroll-epr-queued", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "cfM(Lepr;)V", occurrence = 1)
    public static void scroll_epr_queued(bitwig.obf.dpl input, bitwig.obf.epr e) { if (!Wheel.consume(e)) input.cfM(e); }

    @Hook(id = "scroll-eps", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "MRY(Leps;)V", occurrence = 1)
    public static void scroll_eps(bitwig.obf.dpl input, bitwig.obf.eps e) { if (!Wheel.consume(e)) input.MRY(e); }

    @Hook(id = "scroll-eps-queued", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "cfM(Leps;)V", occurrence = 1)
    public static void scroll_eps_queued(bitwig.obf.dpl input, bitwig.obf.eps e) { if (!Wheel.consume(e)) input.cfM(e); }

    @Hook(id = "scroll-epv", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "MRY(Lepv;)V", occurrence = 1)
    public static void scroll_epv(bitwig.obf.dpl input, bitwig.obf.epv e) { if (!Wheel.consume(e)) input.MRY(e); }

    @Hook(id = "scroll-epv-queued", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "cfM(Lepv;)V", occurrence = 1)
    public static void scroll_epv_queued(bitwig.obf.dpl input, bitwig.obf.epv e) { if (!Wheel.consume(e)) input.cfM(e); }

    @Hook(id = "scroll-eph", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "cfM(Leph;)V", occurrence = 1)
    public static void scroll_eph(bitwig.obf.dpl input, bitwig.obf.eph e) { if (!Wheel.consume(e)) input.cfM(e); }

    @Hook(id = "scroll-epz", target = bitwig.obf.dpl.class, method = "cfM(Leoy;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.dpl.class, call = "cfM(Lepz;)V", occurrence = 1)
    public static void scroll_epz(bitwig.obf.dpl input, bitwig.obf.epz e) { if (!Wheel.consume(e)) input.cfM(e); }

    @Hook(id = "add-mini-panel", target = g2L.class, method = "MRY(Ljsd;ZZ)V", at = At.REPLACE_CALL,
          callOwner = g2L.class, call = "cfM(LdyC;)V", occurrence = 1)
    public static void setRowContent(g2L row, dyC content) {
        dyC result = content;
        boolean log = MiniGraph.DEBUG && diag.getAndIncrement() < 40; // diagnostics: first 40 rows
        try {
            if (log) {
                jsd d0 = row.LEd();
                NBQ c0 = d0 == null ? null : d0.nfq();
                if (MiniGraph.DEBUG) MOD.log("hook: row=" + row.getClass().getName() + " content=" + (content == null ? null : content.getClass().getName())
                        + " device=" + (d0 == null ? null : d0.getClass().getName())
                        + " controls=" + (c0 == null ? null : c0.getClass().getName())
                        + " pages=" + (c0 == null ? -1 : c0.cfM().count()));
            }
            if (MOD.settings().getBoolean("enabled", true) && content instanceof dve header) {
                jsd device = row.LEd(); // the device this row shows
                String name = device == null ? "" : Params.safeName(device);
                if (MiniGraph.DEBUG && (name.equals("Compressor") || name.equals("Compressor+")) && overviewProbed.add(name)) probeOverview(device, name);
                if (NATIVE_PANEL_VIEWS.contains(name)) { // Bitwig's own live display (scope, spectrum) in the mini area
                    if (MiniGraph.DEBUG && name.equals("Oscilloscope")) probe(device);
                    dve scope = name.equals("Oscilloscope") ? scopeWidget(device) : nativePanel(device, name);
                    if (scope != null) {
                        scope = NativeFrame.wrap(scope, device, place()); // right-click: Collapse Mini View
                        Collapse.trackNative(scope, device, place()); // collapse / expand per place (mixer / Inspector)
                        dWb column = dWb.UNj(1);
                        header.MRY(w3.cfM);
                        column.HL(header);
                        column.HL(scope);
                        row.MRY(kn.cfM());
                        result = column;
                    }
                }
                MiniGraph graph = Kinds.create(device); // only the supported devices (by name, or by parameters after a preset)
                if (graph != null) {
                    graph.G_();            // finish building, as Bitwig's widget factories do
                    graph.a_(kn.cfM());    // width: natural (stretches to the channel)
                    graph.MRY(Ixm.cfM());  // height: its preferred height
                    int where = place();
                    if (placeLog.add(name + where)) if (MiniGraph.DEBUG) MOD.log("mini view for " + name + " in " + Collapse.placeName(where));
                    graph.init(where);     // mixer or Inspector, and its remembered collapsed state
                    dve mini = graph;
                    if (log) if (MiniGraph.DEBUG) MOD.log("mini graph for " + name);
                    dWb column = dWb.UNj(1); // vertical group, 1 unit between rows
                    header.MRY(w3.cfM); // keep Bitwig's row line at its usual fixed height
                    column.HL(header); // Bitwig's own row
                    if (Kinds.OVER.equals(Kinds.kind(device))) { // Over: its own before/after clipping display above the curve
                        dve disp = displayPart(device, name, OVER_DISPLAY_HEIGHT);
                        if (disp != null) { disp = NativeFrame.wrap(disp, device, where); Collapse.trackNative(disp, device, where); column.HL(disp); }
                    }
                    column.HL(mini);   // the mini panel
                    row.MRY(kn.cfM()); // height: Bitwig fixed the row to one line; let it fit the column
                    result = column;
                    if (log) if (MiniGraph.DEBUG) MOD.log("mini panel added");
                }
            }
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                MOD.log("could not add a mini panel; showing Bitwig's row", t);
            }
            result = content;
        }
        row.cfM(result); // what Bitwig's code did: set the row's content
    }

    // ---- temporary probe: dump native devices' panel trees to the log (once per device type) ----
    private static final java.util.Set<String> probed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void probe(jsd device) {
        try {
            Object def = device.OzW(); // the device's definition (native devices: R1Z)
            String name = device.aF_();
            if (!(def instanceof bitwig.obf.R1Z native_)) {
                if (name != null && probed.add(name)) if (MiniGraph.DEBUG) MOD.log("PROBE device '" + name + "' def=" + (def == null ? null : def.getClass().getName()) + " (not R1Z)");
                return;
            }
            if (name == null || !probed.add(name)) return;
            StringBuilder sb = new StringBuilder("PROBE device '" + name + "' def=" + def.getClass().getName() + "\n");
            String[] acc = {"vpw", "OzW", "MJ", "rGR", "RDJ", "H2K"};
            for (String a : acc) {
                Object panel = call(native_, a);
                sb.append(" panel ").append(a).append(" = ").append(panel == null ? "null" : panel.getClass().getName()).append("\n");
                if (panel != null) dump(panel, 2, sb, 0);
            }
            for (int i = 0; i < 6; i++) {
                Object panel;
                try { panel = native_.cfM(i); } catch (Throwable t) { break; }
                if (panel == null) continue;
                sb.append(" panel cfM(").append(i).append(") = ").append(panel.getClass().getName()).append("\n");
                dump(panel, 2, sb, 0);
            }
            if (MiniGraph.DEBUG) MOD.log(sb.toString());
        } catch (Throwable t) {
            MOD.log("probe failed", t);
        }
    }

    private static Object call(Object o, String m) {
        try {
            java.lang.reflect.Method mm = findMethod(o.getClass(), m);
            if (mm == null) return null;
            mm.setAccessible(true);
            return mm.invoke(o);
        } catch (Throwable t) { return null; }
    }

    private static java.lang.reflect.Method findMethod(Class<?> c, String m) {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            for (java.lang.reflect.Method mm : k.getDeclaredMethods())
                if (mm.getName().equals(m) && mm.getParameterCount() == 0) return mm;
        for (Class<?> i : allInterfaces(c))
            for (java.lang.reflect.Method mm : i.getDeclaredMethods())
                if (mm.getName().equals(m) && mm.getParameterCount() == 0) return mm;
        return null;
    }

    private static java.util.Set<Class<?>> allInterfaces(Class<?> c) {
        java.util.Set<Class<?>> out = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Class<?>> q = new java.util.ArrayDeque<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) q.addAll(java.util.List.of(k.getInterfaces()));
        while (!q.isEmpty()) { Class<?> i = q.poll(); if (out.add(i)) q.addAll(java.util.List.of(i.getInterfaces())); }
        return out;
    }

    /** Generic walk of Bitwig's object model: oDc.iM()/CXm()/nWt()/upC() list owned children by metaclass field. */
    private static void dump(Object root, int indent, StringBuilder sb, int depth) {
        walk(root, indent, sb, depth, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static void walk(Object o, int indent, StringBuilder sb, int depth, java.util.Set<Object> seen) {
        if (depth > 12 || !seen.add(o) || sb.length() > 60000) return;
        for (String m : new String[] {"iM", "CXm", "nWt", "upC"}) {
            Object r = call(o, m);
            if (!(r instanceof java.util.List<?> list)) continue;
            for (Object c : list) {
                Object item = c;
                if (c instanceof com.bitwig.ramona.core.CbE cbe) { // a list field: expand
                    for (int i = 0; i < cbe.MRY(); i++) emit(cbe.Hnt(i), m + "[]", indent, sb, depth, seen);
                } else if (item instanceof com.bitwig.ramona.core.oDc) {
                    emit(item, m, indent, sb, depth, seen);
                }
            }
        }
    }

    private static final java.util.Set<String> overviewProbed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicBoolean tunerProbed = new java.util.concurrent.atomic.AtomicBoolean();

    /** Log a device's main panel item by item (class, kind, text, nested panels) to find the part to show. */
    static void probePanel(jsd device, String name) {
        try {
            Object def = device.OzW();
            if (!(def instanceof bitwig.obf.R1Z native_)) return;
            Object main = call(native_, "vpw");
            if (!(main instanceof bitwig.obf.dfh panel)) return;
            StringBuilder sb = new StringBuilder("PANEL of " + name + ": " + main.getClass().getName() + "\n");
            panelItems(panel, sb, 1, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
            if (MiniGraph.DEBUG) MOD.log(sb.toString());
        } catch (Throwable t) { MOD.log("panel probe failed", t); }
    }

    private static void panelItems(bitwig.obf.dfh panel, StringBuilder sb, int depth, java.util.Set<Object> seen) {
        if (panel == null || depth > 10 || !seen.add(panel) || sb.length() > 60000) return;
        java.util.List<?> items;
        try { items = panel.IwV(); } catch (Throwable t) { return; }
        if (items == null) return;
        int i = 0;
        for (Object it : items) {
            sb.append("  ".repeat(depth)).append(i++).append(": ").append(it == null ? "null" : it.getClass().getName());
            try { sb.append(" kind=").append(((com.bitwig.ramona.core.oDc) it).ng_().W_()); } catch (Throwable ignored) {}
            for (String m : new String[] {"aF_", "QFI", "BDm", "ax_", "fFs", "qFI"}) {
                Object v = call(it, m);
                if (v instanceof String str && !str.isBlank()) sb.append(" ").append(m).append("='").append(str).append("'");
            }
            String ts = String.valueOf(it);
            if (ts.length() < 120) sb.append(" toString=").append(ts);
            sb.append("\n");
            if (it instanceof bitwig.obf.RlL item) { try { panelItems(item.iCS(), sb, depth + 1, seen); } catch (Throwable ignored) {} }
        }
    }

    /** Log what a device's native overview panel (the one we hide) is made of: where its live levels come from. */
    static void probeOverview(jsd device, String name) {
        try {
            Object def = device.OzW();
            if (!(def instanceof bitwig.obf.R1Z native_)) return;
            bitwig.obf.dfh ov = native_.rGR();
            StringBuilder sb = new StringBuilder("OVERVIEW of " + name + ": " + (ov == null ? "none" : ov.getClass().getName()) + "\n");
            if (ov != null) listItems(ov, sb, 1, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
            if (MiniGraph.DEBUG) MOD.log(sb.toString());
        } catch (Throwable t) { MOD.log("overview probe failed", t); }
    }

    private static void listItems(bitwig.obf.dfh panel, StringBuilder sb, int depth, java.util.Set<Object> seen) {
        if (panel == null || depth > 10 || !seen.add(panel) || sb.length() > 40000) return;
        java.util.List<?> items;
        try { items = panel.IwV(); } catch (Throwable t) { return; }
        if (items == null) return;
        for (Object it : items) {
            String kind = "";
            try { kind = String.valueOf(((com.bitwig.ramona.core.oDc) it).ng_().W_()); } catch (Throwable ignored) {}
            sb.append("  ".repeat(depth)).append(it == null ? "null" : it.getClass().getName()).append(" ").append(kind);
            for (String m : new String[] {"Pgx", "aF_", "QFI", "xL"}) { Object v = call(it, m); if (v != null && !(v instanceof java.util.Collection)) sb.append(" ").append(m).append("=").append(v.getClass().getSimpleName()); }
            sb.append("\n");
            if (it instanceof bitwig.obf.RlL item) { try { listItems(item.iCS(), sb, depth + 1, seen); } catch (Throwable ignored) {} }
        }
    }

    /** Height of the native panels: the same 110 layout units as the MiniGraph views (see FixedHeight). */
    static final long NATIVE_HEIGHT = 110;

    /** Devices whose mini view is Bitwig's own live display, built by Bitwig's panel builder. */
    static final java.util.Set<String> NATIVE_PANEL_VIEWS = java.util.Set.of("Oscilloscope", "Spectrum", "Spectrum Analyzer", "Tuner");

    /** Bitwig's own main panel for the device, built as the mixer builds overviews, 110 units tall; null if it can't. */
    static dve nativePanel(jsd device, String name) {
        try {
            Object def = device.OzW();
            if (!(def instanceof bitwig.obf.R1Z native_)) return null;
            Object main = call(native_, "vpw");
            if (!(main instanceof bitwig.obf.dfh panel)) { MOD.log(name + ": no main panel"); return null; }
            bitwig.obf.aQl built = bitwig.obf.aQl.hUR(panel);
            built.cfM(com.bitwig.base.geom.Dgc.lYc);
            built.a_(kn.cfM());
            built.MRY(new FixedHeight(NATIVE_HEIGHT));
            if (MiniGraph.DEBUG) MOD.log(name + ": built Bitwig's own panel");
            return built;
        } catch (Throwable t) {
            MOD.log(name + " panel failed", t);
            return null;
        }
    }

    // ---- a device's own display, cut out of its panel (Over's before/after clipping history) ----

    static final long OVER_DISPLAY_HEIGHT = 56;
    private static final java.util.Set<String> displayLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Bitwig's own display part of a device's main panel, built alone: the smallest sub-panel that holds a display item
     * (kind names a display / history / meter / scope / spectrum / graph) and no controls (knobs, buttons, choosers…),
     * built by the panel builder the mixer uses (aQl.hUR), as wide as the channel and `height` units tall. The panel's
     * item tree and the choice are logged once per device type. Null if there is no such part.
     */
    static dve displayPart(jsd device, String name, long height) {
        try {
            Object def = device.OzW();
            if (!(def instanceof bitwig.obf.R1Z native_)) return null;
            Object main = call(native_, "vpw");
            if (!(main instanceof bitwig.obf.dfh panel)) return null;
            bitwig.obf.dfh part = displayPanel(panel, 0);
            if (displayLogged.add(name)) {
                StringBuilder sb = new StringBuilder(name + " panel (display part " + (part == null ? "not found" : part == panel ? "= whole panel" : "found") + "):\n");
                panelItems(panel, sb, 1, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
                MOD.log(sb.toString());
            }
            if (part == null || part == panel) return null; // the whole panel would bring its knobs along
            bitwig.obf.aQl built = bitwig.obf.aQl.hUR(part);
            built.cfM(com.bitwig.base.geom.Dgc.lYc);
            built.a_(kn.cfM());
            built.MRY(new FixedHeight(height));
            return built;
        } catch (Throwable t) {
            MOD.log(name + " display failed", t);
            return null;
        }
    }

    static String kindOf(Object item) {
        try { return String.valueOf(((com.bitwig.ramona.core.oDc) item).ng_().W_()).toLowerCase(java.util.Locale.ROOT); } catch (Throwable t) { return ""; }
    }
    static boolean isControl(String k) {
        for (String w : new String[] {"knob", "button", "toggle", "chooser", "number", "field", "slider", "switch", "popup", "menu"}) if (k.contains(w)) return true;
        return false;
    }
    static boolean isDisplay(String k) {
        for (String w : new String[] {"display", "history", "meter", "scope", "spectrum", "graph", "curve", "wave"}) if (k.contains(w)) return true;
        return false;
    }

    /** [has a display, has a control] in the panel's subtree. */
    private static boolean[] contents(bitwig.obf.dfh panel, int depth) {
        boolean[] r = {false, false};
        if (panel == null || depth > 12) return r;
        java.util.List<?> items;
        try { items = panel.IwV(); } catch (Throwable t) { return r; }
        if (items == null) return r;
        for (Object it : items) {
            String k = kindOf(it);
            if (isDisplay(k)) r[0] = true;
            if (isControl(k)) r[1] = true;
            if (it instanceof bitwig.obf.RlL item) {
                try { boolean[] c = contents(item.iCS(), depth + 1); r[0] |= c[0]; r[1] |= c[1]; } catch (Throwable ignored) {}
            }
        }
        return r;
    }

    /** The smallest sub-panel holding a display and no controls (or the panel itself if it qualifies); null if none. */
    private static bitwig.obf.dfh displayPanel(bitwig.obf.dfh panel, int depth) {
        if (panel == null || depth > 12) return null;
        java.util.List<?> items;
        try { items = panel.IwV(); } catch (Throwable t) { return null; }
        if (items != null) for (Object it : items) {
            if (it instanceof bitwig.obf.RlL item) {
                bitwig.obf.dfh sub;
                try { sub = item.iCS(); } catch (Throwable t) { sub = null; }
                bitwig.obf.dfh found = displayPanel(sub, depth + 1);
                if (found != null) return found;
            }
        }
        boolean[] c = contents(panel, depth);
        return c[0] && !c[1] ? panel : null;
    }

    // ---- Oscilloscope: Bitwig's own live scope widget ----

    /**
     * The Oscilloscope device's display is a display_oscilloscope_panel_item whose widget (aSN) Bitwig builds with
     * aSN.cfM(teo). Find that item in the device's panel tree (any object that is a teo, or whose kind names the
     * oscilloscope display) and build the same widget, 110 units tall and as wide as the channel. Null if not found.
     */
    static dve scopeWidget(jsd device) {
        try {
            Object def = device.OzW();
            if (!(def instanceof bitwig.obf.R1Z native_)) { MOD.log("scope: device definition isn't native: " + (def == null ? null : def.getClass().getName())); return null; }
            java.util.List<Object> roots = new java.util.ArrayList<>();
            for (String a : new String[] {"vpw", "OzW", "MJ", "rGR", "RDJ", "H2K"}) { Object o = call(native_, a); if (o != null) roots.add(o); }
            for (int i = 0; i < 6; i++) { try { Object o = native_.cfM(i); if (o != null) roots.add(o); } catch (Throwable t) { break; } }
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Object r : roots) {
                Object item = r instanceof bitwig.obf.dfh panel ? findInPanel(panel, seen, 0) : findScope(r, seen, 0);
                if (item == null) continue;
                if (MiniGraph.DEBUG) MOD.log("scope: found " + item.getClass().getName() + " (teo=" + (item instanceof bitwig.obf.teo) + ")");
                if (!(item instanceof bitwig.obf.teo model)) continue;
                bitwig.obf.aSN w = bitwig.obf.aSN.cfM(model);
                w.a_(kn.cfM());                       // width: stretch to the channel
                w.MRY(new FixedHeight(NATIVE_HEIGHT));       // height: as the other mini views
                if (MiniGraph.DEBUG) MOD.log("scope: live widget added");
                return w;
            }
            if (MiniGraph.DEBUG) MOD.log("scope: no oscilloscope display item found; building Bitwig's own device panel instead");
            // Let Bitwig's panel builder (the one the mixer uses for overviews) build the device's main panel: its
            // factories create the live scope themselves.
            Object main = call(native_, "vpw");
            if (main instanceof bitwig.obf.dfh panel) {
                bitwig.obf.aQl built = bitwig.obf.aQl.hUR(panel);
                built.cfM(com.bitwig.base.geom.Dgc.lYc);
                built.a_(kn.cfM());
                built.MRY(new FixedHeight(NATIVE_HEIGHT));
                if (MiniGraph.DEBUG) MOD.log("scope: built Bitwig's Oscilloscope panel " + built.getClass().getName());
                return built;
            }
        } catch (Throwable t) {
            MOD.log("scope widget failed", t);
        }
        return null;
    }

    /** A device panel (dfh) lists its items (RlL) in IwV(); an item can hold a sub-panel in iCS(). The scope item is a teo. */
    private static Object findInPanel(bitwig.obf.dfh panel, java.util.Set<Object> seen, int depth) {
        if (panel == null || depth > 16 || !seen.add(panel)) return null;
        java.util.List<?> items;
        try { items = panel.IwV(); } catch (Throwable t) { return null; }
        if (items == null) return null;
        for (Object it : items) {
            if (it instanceof bitwig.obf.teo) return it;
            if (scopeLog.getAndIncrement() < 80) if (MiniGraph.DEBUG) MOD.log("scope: item " + (it == null ? null : it.getClass().getName()));
            if (it instanceof bitwig.obf.RlL item) {
                Object f;
                try { f = findInPanel(item.iCS(), seen, depth + 1); } catch (Throwable t) { f = null; }
                if (f != null) return f;
            }
        }
        return null;
    }
    private static final java.util.concurrent.atomic.AtomicInteger scopeLog = new java.util.concurrent.atomic.AtomicInteger();

    private static Object findScope(Object o, java.util.Set<Object> seen, int depth) {
        if (o == null || depth > 14 || !seen.add(o)) return null;
        String kind = "";
        try { kind = String.valueOf(((com.bitwig.ramona.core.oDc) o).ng_().W_()); } catch (Throwable ignored) {}
        if (o instanceof bitwig.obf.teo || kind.contains("oscilloscope")) return o;
        for (String m : new String[] {"iM", "CXm", "nWt", "upC"}) {
            Object r = call(o, m);
            if (!(r instanceof java.util.List<?> list)) continue;
            for (Object c : list) {
                if (c instanceof com.bitwig.ramona.core.CbE cbe) {
                    for (int i = 0; i < cbe.MRY(); i++) { Object f = findScope(cbe.Hnt(i), seen, depth + 1); if (f != null) return f; }
                } else if (c instanceof com.bitwig.ramona.core.oDc) {
                    Object f = findScope(c, seen, depth + 1); if (f != null) return f;
                }
            }
        }
        return null;
    }

    private static void emit(Object item, String via, int indent, StringBuilder sb, int depth, java.util.Set<Object> seen) {
        if (item == null || seen.contains(item)) return;
        String kind = "?";
        try { kind = ((com.bitwig.ramona.core.oDc) item).ng_().W_(); } catch (Throwable ignored) {}
        sb.append(" ".repeat(indent)).append(via).append(": ").append(kind).append(" [").append(item.getClass().getName()).append("]\n");
        walk(item, indent + 2, sb, depth + 1, seen);
    }
}
