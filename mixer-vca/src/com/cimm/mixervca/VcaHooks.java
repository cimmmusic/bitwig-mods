package com.cimm.mixervca;

import bitwig.obf.xJo; // a track
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.document.core.iface.RYd; // a channel
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A VCA's fader bar is red instead of Bitwig's orange; its dB readout keeps the default colour.
 *
 * The mixer builds each channel's volume fader in bDJ.cfM(channel, …) with aoH.tp2() (the fader widget). The fader
 * paints in avb.MRY(SYo), asking its style for the accent colour (eff.eej(), or eff.xL() in another state). So: note
 * the channel while its fader is built, remember the fader widgets built for VCAs, and while one of those paints,
 * answer the style's colour question with red.
 */
public final class VcaHooks {
    private VcaHooks() {}

    /** A Bitwig theme colour (eeY) made from a plain red. */
    static final bitwig.obf.eeY RED = bitwig.obf.eeY.cfM("VCA fader", bitwig.obf.iok.cfM(226, 58, 58));

    private static final ThreadLocal<RYd> building = new ThreadLocal<>();
    private static final Set<Object> vcaFaders = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Object> painting = new ThreadLocal<>();

    // ---- building: which faders belong to VCAs ----

    @Hook(id = "fader-channel", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.ENTRY)
    public static void faderChannel(@Arg(0) RYd channel) { building.set(channel); }

    @Hook(id = "fader-widget", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.aoH.class, call = "tp2()LaoH;", occurrence = 1)
    public static bitwig.obf.aoH faderWidget() {
        bitwig.obf.aoH fader = bitwig.obf.aoH.tp2();
        RYd ch = building.get();
        building.remove();
        try { if (ch instanceof xJo t && Vca.members(t) != null) vcaFaders.add(fader); } catch (Throwable ignored) {}
        return fader;
    }

    // ---- painting: red for those faders ----

    @Hook(id = "fader-paint", target = bitwig.obf.avb.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void faderPaint(@This bitwig.obf.avb fader) { painting.set(vcaFaders.contains(fader) ? fader : null); }

    @Hook(id = "fader-accent-1", target = bitwig.obf.avb.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.eff.class, call = "eej()LeeY;", occurrence = 1)
    public static bitwig.obf.eeY accent1(bitwig.obf.eff style) { return painting.get() != null ? RED : style.eej(); }

    @Hook(id = "fader-accent-2", target = bitwig.obf.avb.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.eff.class, call = "eej()LeeY;", occurrence = 2)
    public static bitwig.obf.eeY accent2(bitwig.obf.eff style) { return painting.get() != null ? RED : style.eej(); }

    @Hook(id = "fader-accent-3", target = bitwig.obf.avb.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.eff.class, call = "xL()LeeY;", occurrence = 1)
    public static bitwig.obf.eeY accent3(bitwig.obf.eff style) { return painting.get() != null ? RED : style.xL(); }
}
