package com.cimm.clipfx;

import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

/**
 * The Clip FX Units are plumbing: hidden from the mixer's Devices section and from the track's device chain (and the
 * chain's add-device + after them). They keep processing. Recognised by their name, so they stay hidden after
 * reopening a project. (The mod's hooks all live in this class.)
 */
public final class MixerHide {
    private MixerHide() {}

    static boolean isUnit(Object device) {
        try { return ClipFx.units.contains(device) || isUnitName(((com.bitwig.flt.document.core.master.device.xlG) device).aF_()); }
        catch (Throwable t) { return false; }
    }

    /** A clip's gate: the first device in the FX layer of a Clip FX Unit (hidden in the Inspector's insert list). */
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

    private static void shrink(bitwig.obf.dve w) {
        w.t_(false);
        try { w.MRY(ZERO); } catch (Throwable ignored) {}
        try { w.a_(ZERO); } catch (Throwable ignored) {}
        try { w.Hnt(ZERO); } catch (Throwable ignored) {}
        w.kJ_();
    }

    // ---- the track's device chain (device panel) ----

    @Hook(id = "hide-unit-in-chain", target = com.bitwig.flt.widget.core.device.htp.class,
          method = "cfM(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj chainRow(@Return com.bitwig.flt.widget.core.device.gRj row, @This com.bitwig.flt.widget.core.device.htp chain, @Arg(0) bitwig.obf.jsd device) {
        row = row(row, device);
        if (row != null && isUnit(device)) hidePlusLater(chain, row);
        return row;
    }

    @Hook(id = "hide-unit-in-chain-2", target = com.bitwig.flt.widget.core.device.htp.class,
          method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj chainRow2(@Return com.bitwig.flt.widget.core.device.gRj row, @This com.bitwig.flt.widget.core.device.htp chain, @Arg(0) bitwig.obf.jsd device) {
        row = row(row, device);
        if (row != null && isUnit(device)) hidePlusLater(chain, row);
        return row;
    }

    /** Hidden units' widgets, with their chain widget: the + after them are hidden (swept twice a second on the UI
     *  thread, as the chain places and rebuilds its widgets). */
    static final java.util.Map<Object, java.lang.ref.WeakReference<Object>> hiddenRows = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static java.util.concurrent.ScheduledExecutorService sweeper;

    static synchronized void hidePlusLater(Object chain, Object row) {
        hiddenRows.put(row, new java.lang.ref.WeakReference<>(chain));
        if (sweeper != null) return;
        sweeper = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "clip-fx-plus"); t.setDaemon(true); return t; });
        sweeper.scheduleAtFixedRate(() -> {
            try { com.bitwig.flt.app.CHR.Grk().asyncExec(MixerHide::sweep); } catch (Throwable ignored) {}
        }, 200, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static void sweep() {
        Object[][] pairs;
        synchronized (hiddenRows) {
            pairs = hiddenRows.entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue().get()}).toArray(Object[][]::new);
        }
        for (Object[] p : pairs) {
            if (p[1] == null) continue;
            try { hidePlus(p[1], p[0]); } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: hiding the unit's + failed", t); }
        }
    }

    private static int plusLogs, missLogs;
    private static final java.util.Set<Object> shrunk = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /** In the chain's widget tree: the list holding the unit's widget (or a widget around it); every + after it there. */
    private static void hidePlus(Object chain, Object row) {
        Object[] found = find(chain, row, 0);
        if (found == null) {
            if (missLogs++ < 3) ClipFx.MOD.log("Clip FX: the unit's widget isn't in its chain (yet): " + kidsOf(chain));
            return;
        }
        java.util.List<?> kids = ((bitwig.obf.dvW) found[0]).jl_();
        int at = (Integer) found[1];
        if (plusLogs++ < 4) ClipFx.MOD.log("Clip FX: chain widgets around the unit (at " + at + "): " + kidsOf(found[0]));
        for (int i = at + 1; i < kids.size(); i++) {
            Object k = kids.get(i);
            if (isPlus(k)) {
                if (shrunk.add(k)) { shrink((bitwig.obf.dve) k); ClipFx.MOD.log("Clip FX: hid a + after the unit (" + k.getClass().getSimpleName() + ")"); }
                break; // the unit's own +; the next ones belong to the devices after it
            }
        }
    }

    /** {list widget, index} of the child (under 'w') that is, or holds, 'target': the deepest one with a + after it. */
    private static Object[] find(Object w, Object target, int depth) {
        if (depth > 6 || !(w instanceof bitwig.obf.dvW box)) return null;
        java.util.List<?> kids;
        try { kids = box.jl_(); } catch (Throwable t) { return null; }
        if (kids == null) return null;
        for (int i = 0; i < kids.size(); i++) {
            Object k = kids.get(i);
            if (k == target || holds(k, target, 0)) {
                if (k != target) { Object[] deeper = find(k, target, depth + 1); if (deeper != null && hasPlusAfter(deeper)) return deeper; }
                return new Object[]{w, i};
            }
        }
        return null;
    }

    private static boolean holds(Object w, Object target, int depth) {
        if (depth > 6 || !(w instanceof bitwig.obf.dvW box)) return false;
        try { for (Object k : box.jl_()) if (k == target || holds(k, target, depth + 1)) return true; } catch (Throwable ignored) {}
        return false;
    }

    private static boolean hasPlusAfter(Object[] f) {
        java.util.List<?> kids = ((bitwig.obf.dvW) f[0]).jl_();
        for (int i = (Integer) f[1] + 1; i < kids.size(); i++) if (isPlus(kids.get(i))) return true;
        return false;
    }

    /** An add-device +: Bitwig's tLF, or a widget holding one. */
    private static boolean isPlus(Object w) {
        // the chain lays out "+ device + device +": the + slots are sxY (seen in the log: sxY L1F sxY)
        if (w instanceof com.bitwig.flt.widget.core.device.sxY || w instanceof com.bitwig.flt.widget.core.device.tLF) return true;
        try { if (w instanceof bitwig.obf.dvW box) for (Object k : box.jl_()) if (k instanceof com.bitwig.flt.widget.core.device.tLF) return true; } catch (Throwable ignored) {}
        return false;
    }

    private static String kidsOf(Object w) {
        StringBuilder b = new StringBuilder();
        try { for (Object k : ((bitwig.obf.dvW) w).jl_()) b.append(k.getClass().getSimpleName()).append(isPlus(k) ? "(+)" : "").append(' '); } catch (Throwable t) { b.append("?"); }
        return b.toString();
    }

    // ---- the mixer's Devices section (and the Inspector's insert list, which is the same widget) ----

    @Hook(id = "hide-unit-in-mixer", target = com.bitwig.flt.widget.core.device.uMi.class,
          method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj row(@Return com.bitwig.flt.widget.core.device.gRj row, @Arg(0) bitwig.obf.jsd device) {
        try {
            if (row != null && (isUnit(device) || isGate(device))) shrink((bitwig.obf.dve) (Object) row);
        } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: hiding a unit in the mixer failed", t); }
        return row;
    }
}
