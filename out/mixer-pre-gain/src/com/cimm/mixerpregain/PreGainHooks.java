package com.cimm.mixerpregain;

import bitwig.obf.Ixm; // size policy: fixed at the widget's preferred size
import bitwig.obf.dWb; // a vertical or horizontal group of widgets
import bitwig.obf.dve; // a widget
import bitwig.obf.jsd; // a device
import bitwig.obf.kn;  // size policy: the widget's natural size
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;

/**
 * Mixer: Pre Gain. A gain knob at the top of each mixer channel (above its devices), like an input gain. Behind it is
 * Bitwig's Tool, inserted automatically and hidden: right after the instrument on instrument tracks (it trims the
 * instrument's output before the effects), first in the chain otherwise.
 */
public final class PreGainHooks {
    private PreGainHooks() {}

    // ---- the knob, at the top of each channel's Devices section (bwC / byu .cfM(track, flag) build that cell) ----
    private static final ThreadLocal<com.bitwig.flt.document.core.iface.RYd> sectionTrack = new ThreadLocal<>();

    @Hook(id = "section-track", target = bitwig.obf.bwC.class, method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;Z)Ldve;", at = At.ENTRY)
    public static void sectionTrack(@Arg(0) com.bitwig.flt.document.core.iface.RYd track) { sectionTrack.set(track); }

    @Hook(id = "section", target = bitwig.obf.bwC.class, method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;Z)Ldve;", at = At.WRAP_RETURN)
    public static dve section(@Return dve devices) { return withKnob(devices); }

    @Hook(id = "section-track-2", target = bitwig.obf.byu.class, method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;Z)Ldve;", at = At.ENTRY)
    public static void sectionTrack2(@Arg(0) com.bitwig.flt.document.core.iface.RYd track) { sectionTrack.set(track); }

    @Hook(id = "section-2", target = bitwig.obf.byu.class, method = "cfM(Lcom/bitwig/flt/document/core/iface/RYd;Z)Ldve;", at = At.WRAP_RETURN)
    public static dve section2(@Return dve devices) { return withKnob(devices); }

    private static dve withKnob(dve devices) {
        com.bitwig.flt.document.core.iface.RYd track = sectionTrack.get();
        sectionTrack.remove();
        try {
            if (devices == null || track == null || !PreGain.enabled()) return devices;
            bitwig.obf.FAu chain = track.Ucl(); // the track's device chain
            if (chain == null) return devices;
            PreGain.ensure(track, chain);
            GainKnob knob = new GainKnob(track, chain, 26);
            knob.G_();
            knob.a_(kn.cfM());
            knob.MRY(Ixm.cfM());
            dWb column = dWb.UNj(2);
            column.HL(knob);    // the pre gain, at the top of the channel
            column.HL(devices); // Bitwig's devices
            return column;
        } catch (Throwable t) {
            Log.MOD.log("pre gain knob failed", t);
            return devices;
        }
    }

    // ---- the Tool behind it is hidden: in the device panel and in the mixer's Devices section ----

    @Hook(id = "panel-device", target = com.bitwig.flt.widget.core.device.THR.class, method = "UNj(Ljsd;)V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.flt.widget.core.device.THR.class, call = "cfM(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", occurrence = 1)
    public static com.bitwig.flt.widget.core.device.gRj panelDevice(com.bitwig.flt.widget.core.device.THR chain, jsd device) {
        return panel(chain.cfM(device), device);
    }

    @Hook(id = "panel-device-2", target = com.bitwig.flt.widget.core.device.THR.class, method = "FzB(Ljsd;)V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.flt.widget.core.device.THR.class, call = "cfM(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", occurrence = 1)
    public static com.bitwig.flt.widget.core.device.gRj panelDevice2(com.bitwig.flt.widget.core.device.THR chain, jsd device) {
        return panel(chain.cfM(device), device);
    }

    /**
     * Device panel: the pre-gain Tool is hidden; the first visible device gets the pre-gain knob in front of it, so the
     * knob reads as the start of the chain (the Tool doing the work sits after the instrument).
     */
    private static com.bitwig.flt.widget.core.device.gRj panel(com.bitwig.flt.widget.core.device.gRj w, jsd device) {
        if (w == null || !PreGain.enabled()) return w;
        try {
            if (PreGain.isPreGain(device)) { w.t_(false); return w; }
            bitwig.obf.FAu chain = device.xL(); // the device's chain
            if (chain == null || PreGain.firstVisible(chain) != device) return w;
            dve content = w.jD_();       // the device's own panel
            GainKnob knob = new GainKnob(null, chain, 40);
            knob.G_();
            knob.a_(Ixm.cfM());
            knob.MRY(Ixm.cfM());
            dWb row = dWb.IwV(4);        // horizontal: knob, then the device
            w.cfM(row);                  // (this lets go of the device's panel)
            row.HL(knob);
            if (content != null) row.HL(content);
        } catch (Throwable t) {
            Log.MOD.log("pre gain device panel failed", t);
        }
        return w;
    }

    /** The mixer's Devices section builds each device's row in uMi.MRY(device): the pre-gain Tool's row is hidden. */
    private static final ThreadLocal<jsd> rowDevice = new ThreadLocal<>();

    @Hook(id = "row-device", target = com.bitwig.flt.widget.core.device.uMi.class, method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.ENTRY)
    public static void rowDevice(@Arg(0) jsd device) { rowDevice.set(device); }

    @Hook(id = "row", target = com.bitwig.flt.widget.core.device.uMi.class, method = "MRY(Ljsd;)Lcom/bitwig/flt/widget/core/device/gRj;", at = At.WRAP_RETURN)
    public static com.bitwig.flt.widget.core.device.gRj row(@Return com.bitwig.flt.widget.core.device.gRj row) {
        jsd d = rowDevice.get();
        rowDevice.remove();
        return hide(row, d);
    }

    private static com.bitwig.flt.widget.core.device.gRj hide(com.bitwig.flt.widget.core.device.gRj w, jsd device) {
        try { if (w != null && PreGain.isPreGain(device)) w.t_(false); } catch (Throwable ignored) {}
        return w;
    }
}
