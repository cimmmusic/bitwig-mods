package com.cimm.mixervca;

import bitwig.obf.xJo; // a track
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import com.bitwig.flt.document.core.iface.RYd; // a channel

/**
 * A VCA's fader is red instead of Bitwig's orange. The mixer builds each channel's volume fader in
 * bDJ.cfM(channel, …): a slider (duY) whose colour is set with duY.Hnt(theme colour) — the accent orange. The entry
 * hook notes which channel is being built; the call hook swaps the colour when that channel is a VCA.
 */
public final class VcaHooks {
    private VcaHooks() {}

    /** A Bitwig theme colour (eeY) made from a plain red. */
    static final bitwig.obf.eeY RED = bitwig.obf.eeY.cfM("VCA fader", bitwig.obf.iok.cfM(226, 58, 58));

    private static final ThreadLocal<RYd> building = new ThreadLocal<>();

    @Hook(id = "fader-channel", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.ENTRY)
    public static void faderChannel(@Arg(0) RYd channel) { building.set(channel); }

    @Hook(id = "fader-colour", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.duY.class, call = "Hnt(Lejt;)V", occurrence = 1)
    public static void faderColour(bitwig.obf.duY slider, bitwig.obf.ejt colour) {
        RYd ch = building.get();
        building.remove();
        boolean vca = false;
        try { vca = ch instanceof xJo t && Vca.members(t) != null; } catch (Throwable ignored) {}
        slider.Hnt(vca ? RED : colour);
    }
}
