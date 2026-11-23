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

    /** "Clip FX Unit" (as loaded) or "Clip FX – <clip name>" (named after its clip). */
    static boolean isUnitName(String n) { return n != null && (n.equals("Clip FX Unit") || n.equals("Clip FX") || n.startsWith("Clip FX – ")); }

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
    static void hidePlusLater(Object row) {
        for (int ms : new int[]{0, 150, 600}) {
            Runnable r = () -> { try { hidePlus(row); } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: hiding the unit's + failed", t); } };
            if (ms == 0) com.bitwig.flt.app.CHR.Grk().asyncExec(r);
            else { javax.swing.Timer tm = new javax.swing.Timer(ms, e -> com.bitwig.flt.app.CHR.Grk().asyncExec(r)); tm.setRepeats(false); tm.start(); }
        }
    }

    private static int plusLogs;

    private static void hidePlus(Object row) {
        // the row may sit in a wrapper: go up until a parent lists it among siblings
        Object child = row;
        for (int up = 0; up < 4; up++) {
            Object parent = Probe.parent(child);
            if (!(parent instanceof bitwig.obf.dvW box)) { child = parent; if (child == null) return; continue; }
            java.util.List<?> kids = box.jl_();
            int at = kids.indexOf(child);
            if (plusLogs++ < 6) {
                StringBuilder b = new StringBuilder("Clip FX: chain widgets around the unit (level " + up + ", at " + at + "):");
                for (Object k : kids) b.append(' ').append(k == child ? "[UNIT]" : k.getClass().getSimpleName());
                ClipFx.MOD.log(b.toString());
            }
            if (at >= 0) {
                Object next = at + 1 < kids.size() ? kids.get(at + 1) : null, prev = at > 0 ? kids.get(at - 1) : null;
                Object plus = next instanceof com.bitwig.flt.widget.core.device.tLF ? next
                            : prev instanceof com.bitwig.flt.widget.core.device.tLF && at == kids.size() - 1 ? prev : null;
                if (plus != null) { shrink((bitwig.obf.dve) plus); return; }
            }
            child = parent;
        }
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
            if (row != null && isUnit(device)) {
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
