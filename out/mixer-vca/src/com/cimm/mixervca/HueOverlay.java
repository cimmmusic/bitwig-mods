package com.cimm.mixervca;

import bitwig.obf.dve; // a widget
import bitwig.obf.iok; // a colour

/**
 * Paints red in "hue" blend mode (HSL_HUE, IOJ.ODA — Bitwig's graphics follow Cairo's operators) over a VCA's volume
 * section, right after it has painted its children: whatever is orange there (the fader's fill and line) turns red at
 * the same brightness; greys, black and white stay. The dB readout (a text widget, dut, near the top) is left out:
 * the tint starts just below it.
 */
final class HueOverlay {
    private HueOverlay() {}

    static final iok RED = iok.cfM(226, 40, 40);

    static void tint(dve section, com.bitwig.graphics.SYo g) {
        double w = section.Sxo(), h = section.yiX();
        double top = 0;
        try { // below the readout: the lowest text widget in the top third of the section
            com.bitwig.base.geom.OLR me = section.ihs();
            if (me != null) {
                java.util.Iterator<?> it = section.lYc(bitwig.obf.dut.class).iterator();
                while (it.hasNext()) {
                    if (!(it.next() instanceof dve t)) continue;
                    com.bitwig.base.geom.OLR p = t.ihs();
                    if (p == null) continue;
                    double y = p.hUR() - me.hUR(), bottom = y + t.yiX();
                    if (y < h / 3 && bottom > top) top = bottom;
                }
            }
        } catch (Throwable ignored) {}
        if (top >= h) return;
        g.qoU();                                  // save (operator included)
        try {
            g.MRY(0, top, w, h - top); g.lYc();   // clip
            g.cfM(com.bitwig.graphics.IOJ.ODA);   // HSL_HUE
            g.Hnt(RED);
            g.MRY(0, top, w, h - top); g.wGc();
        } finally { g.ucQ(); }                    // restore
    }
}
