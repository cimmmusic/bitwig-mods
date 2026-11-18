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
    /** The fader areas to tint (each the readout box's nearest ancestor that also holds the fill), with that readout. */
    private static final java.util.Map<dve, dve> targets = new java.util.WeakHashMap<>();

    @Hook(id = "paint-children", target = dve.class, method = "Hnt(Lcom/bitwig/graphics/SYo;)V", at = At.REPLACE_CALL,
          callOwner = dve.class, call = "b_(Lcom/bitwig/graphics/SYo;)V", occurrence = 1)
    public static void paintChildren(dve widget, com.bitwig.graphics.SYo g) {
        widget.b_(g);
        if (!any) return;
        try {
            if (tintSlider(widget, g)) return;                                // a VCA's volume slider (arrange view)
            if (sections.containsKey(widget)) { findArea(widget); return; } // the readout box: find the fader area above it
            dve readout = targets.get(widget);
            if (readout != null) HueOverlay.tint(widget, readout, g);
        } catch (Throwable t) { if (!logged) { logged = true; Vca.MOD.log("VCA tint failed", t); } }
    }

    /** The readout box's nearest ancestor at least twice as tall and not much wider: the whole fader. */
    private static void findArea(dve readout) {
        if (targets.containsValue(readout)) return;
        double w = readout.Sxo(), h = readout.yiX();
        StringBuilder sb = new StringBuilder("VCA fader: readout " + w + "x" + h + "; up:");
        dve p = readout.RbC();
        for (int i = 0; i < 6 && p != null; i++, p = p.RbC()) {
            sb.append(' ').append(p.getClass().getSimpleName()).append(' ').append(p.Sxo()).append('x').append(p.yiX());
            if (p.yiX() >= h * 2 && p.Sxo() <= w * 1.6) {
                targets.put(p, readout);
                sb.append(" <- tinted");
                p.jbi(); // repaint it
                break;
            }
        }
        if (!logged) { logged = true; Vca.MOD.log(sb.toString()); }
    }

    // ---- arrange view: the track header's volume slider ----

    /**
     * The track header's volume slider (and the mixer fader too) is an aqu, built by aqu.cfM(volume, meter, flag,
     * channel). Its fill isn't painted with a colour we can swap, so it gets the mixer's treatment: after it has
     * painted (paintChildren above), a red hue goes over the whole slider — orange turns red, greys stay.
     */
    private static final java.util.Map<dve, xJo> sliders = new java.util.WeakHashMap<>();
    private static final ThreadLocal<RYd> buildingSlider = new ThreadLocal<>();
    private static boolean loggedSlider;

    @Hook(id = "arrange-fader-channel", target = bitwig.obf.aqu.class,
          method = "cfM(LhSh;Lljb;ZLcom/bitwig/flt/document/core/iface/RYd;)Laqu;", at = At.ENTRY)
    public static void arrangeFaderChannel(@Arg(3) RYd channel) { buildingSlider.set(channel); }

    @Hook(id = "arrange-fader", target = bitwig.obf.aqu.class,
          method = "cfM(LhSh;Lljb;ZLcom/bitwig/flt/document/core/iface/RYd;)Laqu;", at = At.WRAP_RETURN)
    public static bitwig.obf.aqu arrangeFader(@Return bitwig.obf.aqu slider) {
        RYd channel = buildingSlider.get();
        buildingSlider.remove();
        try {
            if (slider != null && channel instanceof xJo t) {
                sliders.put(slider, t);
                any = true;
                if (!loggedSlider && Vca.members(t) != null) { loggedSlider = true; Vca.MOD.log("VCA volume slider found for '" + t.ca_() + "'"); }
            }
        } catch (Throwable t) { Vca.MOD.log("VCA slider mark failed", t); }
        return slider;
    }

    /**
     * As the mixer's fix: the tint goes on from the slider's PARENT, after the parent's children have painted — by then
     * the slider has painted everything, its fill included (tinting from the slider itself came too early: its fill is
     * painted after its children). When a VCA's slider paints, its parent is noted; when that parent has painted its
     * children, the slider's rectangle in it gets the red hue.
     */
    private static final java.util.Map<dve, dve> sliderParents = new java.util.WeakHashMap<>();

    /**
     * The arrange view's track header (bCO) uses a horizontal slider (aoL), bound to the track's volume after it is
     * built: bCO.Hnt(track) calls slider.Hnt(volume) — aot.Hnt(uN3), the bind method every control shares. Each aoL's
     * bound parameter is noted; the scan below tints those bound to a VCA's volume.
     */
    private static final java.util.Map<dve, Object> sliderParams = new java.util.WeakHashMap<>();

    @Hook(id = "slider-bind", target = bitwig.obf.aot.class, method = "Hnt(LuN3;)V", at = At.ENTRY)
    public static void sliderBind(@bml.api.This bitwig.obf.aot control, @Arg(0) Object param) {
        if (!(control instanceof bitwig.obf.aoL)) return;
        if (param == null) sliderParams.remove(control); else { sliderParams.put(control, param); any = true; }
    }

    /** Whether the slider shows a VCA's volume (mixer aqu by its track, arrange aoL by its bound parameter). */
    private static boolean vcaSlider(dve slider) {
        xJo t = sliders.get(slider);
        if (t != null) return Linker.isVca(t);
        Object p = sliderParams.get(slider);
        return p != null && Linker.isVcaVolume(p);
    }

    /** From the VCA scan (UI thread, twice a second): note each VCA slider's parent, so its tint doesn't depend on the
     *  slider's own paint reaching our hook. */
    static void refreshSliders() {
        java.util.List<dve> all = new java.util.ArrayList<>(sliders.keySet());
        all.addAll(sliderParams.keySet());
        for (dve slider : all) {
            if (slider == null || !vcaSlider(slider)) continue;
            dve parent = slider.RbC();
            if (parent != null && sliderParents.put(parent, slider) != slider) {
                parent.jbi();
                if (loggedParents.add(slider.getClass().getSimpleName())) { Vca.MOD.log("VCA slider " + slider.getClass().getSimpleName() + ": tinting from its parent " + parent.getClass().getSimpleName() + " " + parent.Sxo() + "x" + parent.yiX() + ", slider " + slider.Sxo() + "x" + slider.yiX()); }
            }
        }
    }
    private static final java.util.Set<String> loggedParents = new java.util.HashSet<>();

    static boolean tintSlider(dve widget, com.bitwig.graphics.SYo g) {
        dve slider = sliderParents.get(widget);
        if (slider == null) return false;
        if (!vcaSlider(slider) || slider.RbC() != widget) { sliderParents.remove(widget); return false; }
        com.bitwig.base.geom.OLR p = widget.ihs(), c = slider.ihs();
        if (p == null || c == null) return false;
        double x = c.FzB() - p.FzB(), y = c.hUR() - p.hUR();
        HueOverlay.tintRect(g, x, y, slider.Sxo(), slider.yiX());
        return false; // the parent itself may also be a mixer fader area: carry on
    }
}
