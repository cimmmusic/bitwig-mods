package com.cimm.clipfx;

import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;

/**
 * The Clip FX Units are plumbing: hidden from the mixer's Devices section. They keep processing, and stay in the device
 * chain. Recognised by their name (the preset's, "Clip FX Unit"), so they stay hidden after reopening a project.
 * A result hook on the section's row builder, so it sits beside other mods' hooks inside that method.
 */
public final class MixerHide {
    private MixerHide() {}

    static boolean isUnit(Object device) {
        try { return ClipFx.units.contains(device) || isUnitName(((com.bitwig.flt.document.core.master.device.xlG) device).aF_()); }
        catch (Throwable t) { return false; }
    }

    /** A clip's gate: the first device in the FX layer of a Clip FX Unit (shown in the Inspector's insert list). */
    static boolean isGate(Object device) {
        try {
            Object chain = Probe.parent(device);
            if (!(chain instanceof com.bitwig.flt.document.core.master.device.ISf c) || c.HL().indexOf(device) != 0) return false;
            for (Object x = Probe.parent(chain); x != null; x = Probe.parent(x)) if (isUnit(x)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /** "Clip FX Unit" (as loaded) or "Clip FX – <clip name>" (named after its clip). */
    static boolean isUnitName(String n) { return n != null && (n.equals("Clip FX Unit") || n.equals("Clip FX") || n.startsWith("Clip FX – ")); }

    /** Bitwig closes an undo step: the Clip FX settings stored in the project follow, inside the same step. */
    @Hook(id = "store-in-step", target = com.bitwig.ramona.core.XKG.class, method = "cfM(Z)Z", at = At.ENTRY)
    public static void inStep() { Store.inStep(); }

    private static int fails;

    /** A size of nothing: min, preferred and max 0. */
    private static final bitwig.obf.svm ZERO = new bitwig.obf.svm() {
        @Override public long Hnt(bitwig.obf.ruA r) { return 0; }
        @Override public long MRY(bitwig.obf.ruA r) { return 0; }
        @Override public long cfM(bitwig.obf.ruA r) { return 0; }
    };

    // the device panel's chain (the track's devices): a unit is hidden there too; what's inside it (the gate and the
    // clip's inserts) still shows when the clip's effects are opened (Show FX, double-clicking an insert)
    @Hook(id = "hide-unit-in-chain", target = com.bitwig.flt.widget.core.device.htp.class,
          method = "cfM(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj chainRow(@Return com.bitwig.flt.widget.core.device.gRj row, @Arg(0) bitwig.obf.jsd device) {
        row = row(row, device);
        if (row != null && isUnit(device)) hidePlusLater(row);
        return row;
    }

    /** The chain puts an add-device "+" (tLF) beside every device: the hidden unit's own one is hidden too, once
     *  the chain has placed its widgets (checked a few times, as they arrive late). */
    /** Hidden units' chain widgets: their + are hidden as soon as the chain has placed them, then kept hidden. */
    static final java.util.Set<Object> hiddenRows = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static java.util.concurrent.ScheduledExecutorService sweeper;

    static synchronized void hidePlusLater(Object row) {
        hiddenRows.add(row);
        if (sweeper != null) return;
        sweeper = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "clip-fx-plus"); t.setDaemon(true); return t; });
        sweeper.scheduleAtFixedRate(() -> {
            try { com.bitwig.flt.app.CHR.Grk().asyncExec(MixerHide::sweep); } catch (Throwable ignored) {}
        }, 200, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static void sweep() {
        Object[] rows; synchronized (hiddenRows) { rows = hiddenRows.toArray(); }
        for (Object r : rows) { try { hidePlus(r); } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: hiding the unit's + failed", t); } }
    }

    private static int plusLogs, parentLogs;
    private static final java.util.Set<Object> shrunk = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private static void hidePlus(Object row) {
        // the row may sit in a wrapper: go up until a parent lists it among siblings
        Object child = row;
        for (int up = 0; up < 6; up++) {
            Object parent = widgetParent(child);
            if (up == 0 && parentLogs++ < 4) ClipFx.MOD.log("Clip FX: unit widget's parent: " + (parent == null ? "none yet" : parent.getClass().getName()));
            if (parent == null) return;
            if (!(parent instanceof bitwig.obf.dvW box)) { child = parent; continue; }
            java.util.List<?> kids = box.jl_();
            int at = kids.indexOf(child);
            if (plusLogs++ < 6) {
                StringBuilder b = new StringBuilder("Clip FX: chain widgets around the unit (level " + up + ", at " + at + "):");
                for (Object k : kids) b.append(' ').append(k == child ? "[UNIT]" : k.getClass().getSimpleName());
                ClipFx.MOD.log(b.toString());
            }
            if (at >= 0) { // every + after the unit
                boolean any = false;
                for (int i = at + 1; i < kids.size(); i++)
                    if (isPlus(kids.get(i))) { if (shrunk.add(kids.get(i))) { shrink((bitwig.obf.dve) kids.get(i)); if (plusLogs < 10) ClipFx.MOD.log("Clip FX: hid a + after the unit (" + kids.get(i).getClass().getSimpleName() + ")"); } any = true; }
                if (any) return;
            }
            child = parent;
        }
    }

    /** An add-device +: Bitwig's tLF, or a widget holding one. */
    private static boolean isPlus(Object w) {
        if (w instanceof com.bitwig.flt.widget.core.device.tLF) return true;
        try { if (w instanceof bitwig.obf.dvW box) for (Object k : box.jl_()) if (k instanceof com.bitwig.flt.widget.core.device.tLF) return true; } catch (Throwable ignored) {}
        return false;
    }

    /** A widget's parent widget (the layout tree, as widgets are document objects too). */
    private static Object widgetParent(Object w) {
        try { Object p = ((bitwig.obf.dve) w).Wov(); if (p != null) return p; } catch (Throwable ignored) {}
        return Probe.parent(w);
    }

    private static void shrink(bitwig.obf.dve w) {
        w.t_(false);
        try { w.MRY(ZERO); } catch (Throwable ignored) {}
        try { w.a_(ZERO); } catch (Throwable ignored) {}
        try { w.Hnt(ZERO); } catch (Throwable ignored) {}
        w.kJ_();
    }

    @Hook(id = "hide-unit-in-chain-2", target = com.bitwig.flt.widget.core.device.htp.class,
          method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj chainRow2(@Return com.bitwig.flt.widget.core.device.gRj row, @Arg(0) bitwig.obf.jsd device) {
        row = row(row, device);
        if (row != null && isUnit(device)) hidePlusLater(row);
        return row;
    }

    @Hook(id = "hide-unit-in-mixer", target = com.bitwig.flt.widget.core.device.uMi.class,
          method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj row(@Return com.bitwig.flt.widget.core.device.gRj row, @Arg(0) bitwig.obf.jsd device) {
        try {
            if (row != null && (isUnit(device) || isGate(device))) {
                bitwig.obf.dve w = (bitwig.obf.dve) (Object) row;
                w.t_(false);                                  // not shown
                try { w.MRY(ZERO); } catch (Throwable ignored) {}  // no height
                try { w.a_(ZERO); } catch (Throwable ignored) {}   // no width (a chain lays out left to right)
                try { w.Hnt(ZERO); } catch (Throwable ignored) {}
                w.kJ_();                                      // its parents lay out without it
            }
        } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: hiding a unit in the mixer failed", t); }
        return row;
    }
}
