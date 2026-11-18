package com.cimm.mixervca;

import bitwig.obf.dve; // a widget
import bitwig.obf.xJo; // a track
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import com.bitwig.flt.document.core.iface.RYd; // a channel

/**
 * A VCA's fader is red instead of Bitwig's orange; its dB readout keeps its colour.
 *
 * The mixer builds each channel's volume section (fader, meter, readout) in bDJ.cfM(channel, …). For a VCA the section
 * comes back stacked (dVK, as Bitwig stacks the fader's own layers) under a HueOverlay, which turns its orange red.
 * (Changing the theme colour itself didn't reach the bar: its colour isn't looked up per widget.)
 * The entry hook also registers the channel's project, so saved VCAs link as soon as the mixer is shown.
 */
public final class VcaHooks {
    private VcaHooks() {}

    private static final ThreadLocal<RYd> building = new ThreadLocal<>();

    @Hook(id = "fader-channel", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.ENTRY)
    public static void faderChannel(@Arg(0) RYd channel) {
        building.set(channel);
        try { if (channel instanceof xJo t) Linker.know(t.cb_()); } catch (Throwable ignored) {} // its project
    }

    @Hook(id = "fader-section", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.WRAP_RETURN)
    public static dve faderSection(@Return dve section) {
        RYd ch = building.get();
        building.remove();
        try {
            if (section != null && ch instanceof xJo t && Vca.members(t) != null) { sections.put(section, Boolean.TRUE); any = true; }
        } catch (Throwable t) { Vca.MOD.log("VCA fader mark failed", t); }
        return section;
    }

    /** The VCAs' volume sections (UI thread only). */
    private static final java.util.Map<dve, Boolean> sections = new java.util.WeakHashMap<>();
    private static volatile boolean any;
    private static boolean logged;

    /**
     * Every widget paints in dve.Hnt(g): its background, its content, then its children (b_), then extras. Right after
     * a VCA section has painted its children (the fader's fill and line), the red hue goes over them — the section keeps
     * its own size and place.
     */
    @Hook(id = "paint-children", target = dve.class, method = "Hnt(Lcom/bitwig/graphics/SYo;)V", at = At.REPLACE_CALL,
          callOwner = dve.class, call = "b_(Lcom/bitwig/graphics/SYo;)V", occurrence = 1)
    public static void paintChildren(dve widget, com.bitwig.graphics.SYo g) {
        widget.b_(g);
        if (!any || !sections.containsKey(widget)) return;
        try {
            HueOverlay.tint(widget, g);
            if (!logged) { logged = true; Vca.MOD.log("VCA fader tinted: " + widget.Sxo() + "x" + widget.yiX()); }
        } catch (Throwable t) { if (!logged) { logged = true; Vca.MOD.log("VCA tint failed", t); } }
    }
}
