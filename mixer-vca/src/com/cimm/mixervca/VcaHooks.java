package com.cimm.mixervca;

import bitwig.obf.dve; // a widget
import bitwig.obf.iok; // a colour
import bitwig.obf.xJo; // a track
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import com.bitwig.flt.document.core.iface.RYd; // a channel
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A VCA's fader is red instead of Bitwig's orange; the dB readout keeps its own colour.
 *
 * The mixer builds each channel's volume section (fader, meter, readout) in bDJ.cfM(channel, …). Which inner widget
 * paints the bar isn't certain, so the colour is changed where every themed colour is worked out: eeY.cfM(widget)
 * turns a theme colour into the colour a widget draws with. When that colour is the orange accent (or a shade of it)
 * and the widget is inside a VCA's volume section, the answer is red, shade kept.
 */
public final class VcaHooks {
    private VcaHooks() {}

    static final iok RED = iok.cfM(226, 58, 58);

    /** The volume sections built for VCA tracks. */
    private static final Set<dve> vcaSections = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile boolean any;
    private static final ThreadLocal<RYd> building = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> buildingVca = ThreadLocal.withInitial(() -> false);

    @Hook(id = "fader-channel", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.ENTRY)
    public static void faderChannel(@Arg(0) RYd channel) {
        building.set(channel);
        boolean vca = false;
        try { vca = channel instanceof xJo t && Vca.members(t) != null; } catch (Throwable ignored) {}
        buildingVca.set(vca);   // Bitwig works out the fader's colours while building it, before it is in the section
        if (vca) any = true;
        try { if (channel instanceof xJo t) Linker.know(t.cb_()); } catch (Throwable ignored) {} // its project: VCAs link once the mixer shows
    }
    private static int sectionLogs, hitLogs, missLogs;

    @Hook(id = "fader-section", target = bitwig.obf.bDJ.class,
          method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;LAQw;)Ldve;", at = At.WRAP_RETURN)
    public static dve faderSection(@Return dve section) {
        RYd ch = building.get();
        building.remove();
        buildingVca.set(false);
        try {
            boolean vca = section != null && ch instanceof xJo t && Vca.members(t) != null;
            if (vca) { vcaSections.add(section); any = true; }
            if (vca && sectionLogs < 6) { sectionLogs++; Vca.MOD.log("fader section for '" + (ch instanceof xJo t ? t.ca_() : String.valueOf(ch)) + "' vca=" + vca + " " + (section == null ? "null" : section.getClass().getName())); }
        } catch (Throwable ignored) {}
        return section;
    }

    /** The widgets whose themed colour is being worked out (eeY.cfM can nest: a colour made from another). */
    private static final ThreadLocal<java.util.ArrayDeque<Object>> asking = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static final Object NONE = new Object();

    @Hook(id = "theme-colour-widget", target = bitwig.obf.eeY.class, method = "cfM(Ldve;)Liok;", at = At.ENTRY)
    public static void themeColourWidget(@Arg(0) dve widget) {
        if (!any) return;
        java.util.ArrayDeque<Object> st = asking.get();
        if (st.size() > 64) st.clear(); // never let a lost entry pile up
        st.push(widget == null ? NONE : widget);
    }

    /** Every themed colour a widget draws with: orange inside a VCA's volume section becomes red. */
    @Hook(id = "theme-colour", target = bitwig.obf.eeY.class, method = "cfM(Ldve;)Liok;", at = At.WRAP_RETURN)
    public static iok themeColour(@Return iok colour) {
        if (!any) return colour;
        java.util.ArrayDeque<Object> st = asking.get();
        Object top = st.poll();
        if (!(top instanceof dve widget) || colour == null || !orange(colour)) return colour;
        if (widget instanceof bitwig.obf.dut) return colour; // a text (the dB readout) keeps its own colour
        if (buildingVca.get()) { // inside bDJ.cfM for a VCA: its own widgets, being built
            if (hitLogs < 10) { hitLogs++; Vca.MOD.log("red (while building) for " + widget.getClass().getName() + " " + colour); }
            return redLike(colour);
        }
        try {
            dve w = widget;
            for (int i = 0; i < 12 && w != null; i++) {
                if (w instanceof bitwig.obf.dut) return colour;  // a text: the dB readout keeps its own colour
                if (vcaSections.contains(w)) {
                    if (hitLogs < 10) { hitLogs++; Vca.MOD.log("red for " + widget.getClass().getName() + " depth " + i + " colour " + colour); }
                    return redLike(colour);
                }
                w = w.RbC(); // its parent
            }
            if (missLogs < 5) { missLogs++; Vca.MOD.log("orange outside VCA sections: " + widget.getClass().getName() + " " + colour); }
        } catch (Throwable ignored) {}
        return colour;
    }

    /** Bitwig's orange accent and its darker / lighter shades: red clearly highest, then green, little blue. */
    static boolean orange(iok c) {
        float r = c.FzB(), g = c.IwV(), b = c.UNj(); // iok: FzB red, IwV green, UNj blue, hUR alpha
        return r > 0.25f && g > r * 0.35f && g < r * 0.8f && b < r * 0.45f;
    }

    /** Red at the same brightness and alpha as the orange it replaces. */
    static iok redLike(iok c) {
        float k = c.FzB() / (226f / 255f);
        return new iok(Math.min(1f, RED.FzB() * k), Math.min(1f, RED.IwV() * k), Math.min(1f, RED.UNj() * k), c.hUR());
    }
}
