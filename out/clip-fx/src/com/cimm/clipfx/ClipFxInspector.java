package com.cimm.clipfx;

import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** The Inspector's CLIP FX block, for clips (arranger, launcher) and audio events (in the Note Inspector panel). */
public final class ClipFxInspector implements InspectorProvider {

    @Override
    public void declare(Blocks blocks) {
        blocks.block("clip-fx", "Clip FX").in(Context.ARRANGER_CLIP, Context.LAUNCHER_CLIP, Context.NOTE).bottom()
              .content(ClipFxInspector::content);
    }

    static final int SLOTS = 8;

    private static Object content(BlockContext context) {
        Object selection = selectionOf(context.panel());
        try { com.bitwig.flt.document.core.master.g2L p = Store.project(first(selection)); if (p != null) Store.loadAll(p); } catch (Throwable ignored) {}
        InsertRows list = new InsertRows();
        list.show(fxChainOf(first(selection)));
        remember(context, () -> {
            Object clip = first(selection);
            try { com.bitwig.flt.document.core.master.g2L p = Store.project(clip); if (p != null) Store.loadAll(p); } catch (Throwable ignored) {}
            list.show(fxChainOf(clip));
            Object unit = ClipFx.shown(clip).unit;
            if (unit != null) ClipGate.followName(clip, unit); // a renamed clip renames its unit
        });
        return Rows.column(
                Rows.choice(context, "Clip FX", new String[]{"Off", "On"},
                        () -> ClipFx.shown(first(selection)).on ? 1 : 0,
                        v -> {
                            for (Object o : selected(selection)) {
                                ClipFx.Fx f = ClipFx.edit(o);
                                f.on = v == 1;
                                boolean on = f.on;
                                // the project is edited after Bitwig has finished handling this change (never inside it)
                                List<Object> was = selected(selection);
                                com.bitwig.flt.app.CHR.Grk().asyncExec(() -> {
                                    if (f.unit == null) { if (on) f.unit = ClipGate.turnOn(o); }
                                    else ClipGate.set(o, (com.bitwig.flt.document.core.master.device.AxS) f.unit, on);
                                    reselect(selection, was); // writing the gate moves Bitwig's selection: back to the clip
                                    Rows.refresh(context);
                                });
                            }
                        }).row(),
                Rows.when(context, () -> ClipFx.shown(first(selection)).on,
                        Rows.text(context, "Inserts", () -> inserts(first(selection)).isEmpty() ? "none yet" : ""),
                        list.group,
                        Rows.button(context, "Show FX", () -> showFx(context, first(selection)))));
    }

    /** The clip's FX layer chain (its gate and inserts), or null. */
    static Object fxChainOf(Object clip) {
        try {
            Object unit = ClipFx.shown(clip).unit;
            return unit instanceof com.bitwig.flt.document.core.master.device.AxS u ? ClipGate.fxChain(u) : null;
        } catch (Throwable t) { return null; }
    }

    /** The clip's inserts: the devices of its unit's FX layer after the gate. */
    static List<Object> inserts(Object clip) {
        List<Object> out = new ArrayList<>();
        try {
            Object unit = ClipFx.shown(clip).unit;
            if (!(unit instanceof com.bitwig.flt.document.core.master.device.AxS u)) return out;
            com.bitwig.flt.document.core.master.device.ISf fx = ClipGate.fxChain(u);
            if (fx == null) return out;
            List<?> all = fx.HL();
            for (int i = 1; i < all.size(); i++) out.add(all.get(i)); // device 0 is the gate
        } catch (Throwable ignored) {}
        return out;
    }

    static String name(Object device) {
        try { return ((com.bitwig.flt.document.core.master.device.xlG) device).aF_(); } catch (Throwable t) { return device.getClass().getSimpleName(); }
    }

    /** Selects these clips again (Bitwig's own selection controller for the Inspector's selection). */
    static void reselect(Object selection, List<Object> items) {
        try {
            if (items.isEmpty() || !(selection instanceof bitwig.obf.OXq s)) return;
            bitwig.obf.ICh controller = s.wYK();
            if (items.size() == 1) controller.UNj((com.bitwig.ramona.core.OLR) items.get(0)); else controller.cfM(items);
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: selecting the clip again failed", t); }
    }

    /** Shows the clip's effect chain in the device panel, as double-clicking an insert does. */
    private static void showFx(BlockContext context, Object clip) {
        Object unit = ClipFx.shown(clip).unit;
        if (!(unit instanceof com.bitwig.flt.document.core.master.device.AxS u)) { ClipFx.MOD.log("Clip FX: turn Clip FX on first"); return; }
        com.bitwig.flt.document.core.master.device.ISf fx = ClipGate.fxChain(u);
        if (fx == null || fx.HL().isEmpty()) return;
        List<?> devices = fx.HL();
        Object show = devices.size() > 1 ? devices.get(1) : devices.get(0); // the first insert, else the gate
        try { com.bitwig.flt.widget.core.device.g2L.cfM((bitwig.obf.dve) (Object) context.panel(), (bitwig.obf.jsd) show); }
        catch (Throwable t) { ClipFx.MOD.log("Clip FX: showing the effects failed", t); }
    }

    /** Bitwig's own insert browser, adding at the end of the clip's FX layer (after its gate). */
    private static void addInsert(BlockContext context, Object clip) {
        Object unit = ClipFx.shown(clip).unit;
        if (!(unit instanceof com.bitwig.flt.document.core.master.device.AxS u)) { ClipFx.MOD.log("Clip FX: turn Clip FX on first"); return; }
        com.bitwig.flt.document.core.master.device.ISf fx = ClipGate.fxChain(u);
        if (fx == null) { ClipFx.MOD.log("Clip FX: the clip's FX layer wasn't found"); return; }
        try {
            bitwig.obf.aXu.MRY((bitwig.obf.dve) (Object) context.panel(), (bitwig.obf.FAu) (Object) fx);
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: opening the insert browser failed", t); }
    }

    // ---- the insert list follows what the browser adds: blocks on screen read their rows again every second ----
    private static final List<java.lang.ref.WeakReference<BlockContext>> live = new ArrayList<>();
    private static final java.util.Map<BlockContext, Runnable> updaters = new java.util.WeakHashMap<>();
    private static java.util.concurrent.ScheduledExecutorService timer;

    private static synchronized void remember(BlockContext context, Runnable update) {
        updaters.put(context, update);
        live.add(new java.lang.ref.WeakReference<>(context));
        while (live.size() > 8) live.remove(0);
        if (timer != null) return;
        timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "clip-fx-refresh"); t.setDaemon(true); return t; });
        timer.scheduleAtFixedRate(() -> {
            try { com.bitwig.flt.app.CHR.Grk().asyncExec(ClipFxInspector::refreshSoon); } catch (Throwable ignored) {}
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Every shown Clip FX block reads its inserts again now. UI thread. */
    static void refreshSoon() {
        List<java.lang.ref.WeakReference<BlockContext>> all;
        synchronized (ClipFxInspector.class) { all = new ArrayList<>(live); }
        for (var r : all) {
            BlockContext c = r.get();
            if (c == null) continue;
            Runnable u; synchronized (ClipFxInspector.class) { u = updaters.get(c); }
            try { if (u != null) u.run(); Rows.refresh(c); } catch (Throwable ignored) {}
        }
    }

    private static Object first(Object selection) { List<Object> s = selected(selection); return s.isEmpty() ? null : s.get(0); }

    static Object selectionOf(Object panel) {
        try { return ((bitwig.obf.aLZ) panel).vpw(); } catch (Throwable t) { ClipFx.MOD.log("Inspector selection not found", t); return null; }
    }

    static List<Object> selected(Object selection) {
        List<Object> out = new ArrayList<>();
        try {
            if (selection instanceof bitwig.obf.OXq s) { int n = s.Guv(); for (int i = 0; i < n && i < 256; i++) out.add(s.cfM(i)); }
        } catch (Throwable t) { ClipFx.MOD.log("reading the Inspector selection failed", t); }
        return out;
    }
}
