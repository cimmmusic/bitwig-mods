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

    @Hook(id = "hide-unit-in-mixer", target = com.bitwig.flt.widget.core.device.uMi.class,
          method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj row(@Return com.bitwig.flt.widget.core.device.gRj row, @Arg(0) bitwig.obf.jsd device) {
        try {
            if (row != null && isUnit(device)) {
                bitwig.obf.dve w = (bitwig.obf.dve) (Object) row;
                w.t_(false); // not shown
                w.kJ_();     // its parents lay out without it
            }
        } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: hiding a unit in the mixer failed", t); }
        return row;
    }
}
