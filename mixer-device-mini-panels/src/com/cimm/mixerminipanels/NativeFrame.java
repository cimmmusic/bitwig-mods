package com.cimm.mixerminipanels;

import bitwig.obf.dKW; // a popup menu
import bitwig.obf.dve; // a widget
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.jsd; // a device

/**
 * A frame round one of Bitwig's own panels used as a mini view (Tuner, Oscilloscope, Spectrum, Over's display): Bitwig's
 * panel widgets have no right-click menu, so right-clicks reach this frame, which offers the same "Collapse Mini View"
 * as the drawn views. It is Bitwig's group widget (dWb) built as dWb.Hnt / UNj build theirs.
 */
final class NativeFrame extends bitwig.obf.dWb {
    final jsd device;
    final int place;

    private NativeFrame(jsd device, int place) { this.device = device; this.place = place; }

    static NativeFrame wrap(dve inner, jsd device, int place) {
        NativeFrame f = new NativeFrame(device, place);
        f.FzB(com.bitwig.base.geom.LWw.MRY); // vertical, as dWb.UNj
        f.G_();                               // finish building
        f.TMF(0);                             // no gap
        f.a_(bitwig.obf.kn.cfM());            // width: stretch to the channel
        f.HL(inner);
        return f;
    }

    /** Bitwig only opens a widget's own right-click menu when it says it has one. */
    @Override public boolean iZ_() { return true; }

    @Override public void cfM(dKW popup, double x, double y, eoW mods) {
        super.cfM(popup, x, y, mods);
        try {
            popup.cfM("Collapse Mini View", (Runnable) () -> Collapse.set(device, place, true));
        } catch (Throwable t) { MiniGraph.MOD.log("menu failed", t); }
    }
}
