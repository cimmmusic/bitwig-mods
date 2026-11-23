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
        remember(context);
        Object[] rows = new Object[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            int at = i;
            rows[i] = Rows.when(context, () -> inserts(first(selection)).size() > at,
                    Rows.text(context, null, () -> { List<Object> l = inserts(first(selection)); return l.size() > at ? "   " + name(l.get(at)) : ""; }));
        }
        return Rows.column(
                Rows.choice(context, "Clip FX", new String[]{"Off", "On"},
                        () -> ClipFx.shown(first(selection)).on ? 1 : 0,
                        v -> {
                            for (Object o : selected(selection)) {
                                ClipFx.Fx f = ClipFx.edit(o);
                                f.on = v == 1;
                                boolean on = f.on;
                                // the project is edited after Bitwig has finished handling this change (never inside it)
                                com.bitwig.flt.app.CHR.Grk().asyncExec(() -> {
                                    if (f.unit == null) { if (on) f.unit = ClipGate.turnOn(o); }
                                    else ClipGate.set(o, (com.bitwig.flt.document.core.master.device.AxS) f.unit, on);
                                    Rows.refresh(context);
                                });
                            }
                        }).row(),
                Rows.when(context, () -> ClipFx.shown(first(selection)).on,
                        Rows.number(context, "Tail", 0, 30, 0, " s", false,
                                () -> ClipFx.shown(first(selection)).tail,
                                v -> { for (Object o : selected(selection)) ClipFx.edit(o).tail = v; }),
                        Rows.text(context, "Inserts", () -> inserts(first(selection)).isEmpty() ? "none yet" : ""),
                        Rows.column(rows),
                        Rows.button(context, "+ Add insert", () -> addInsert(context, first(selection)))));
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
    private static java.util.concurrent.ScheduledExecutorService timer;

    private static synchronized void remember(BlockContext context) {
        live.add(new java.lang.ref.WeakReference<>(context));
        while (live.size() > 8) live.remove(0);
        if (timer != null) return;
        timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "clip-fx-refresh"); t.setDaemon(true); return t; });
        timer.scheduleAtFixedRate(() -> {
            try {
                com.bitwig.flt.app.CHR.Grk().asyncExec(() -> {
                    List<java.lang.ref.WeakReference<BlockContext>> all;
                    synchronized (ClipFxInspector.class) { all = new ArrayList<>(live); }
                    for (var r : all) { BlockContext c = r.get(); if (c != null) try { Rows.refresh(c); } catch (Throwable ignored) {} }
                });
            } catch (Throwable ignored) {}
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
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
