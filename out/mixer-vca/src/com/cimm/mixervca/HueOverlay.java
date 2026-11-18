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

    /** Tints the fader area below its readout box. */
    static void tint(dve area, dve readout, com.bitwig.graphics.SYo g) {
        double w = area.Sxo(), h = area.yiX();
        double top = 0;
        com.bitwig.base.geom.OLR a = area.ihs(), r = readout.ihs();
        if (a != null && r != null) top = Math.max(0, r.hUR() - a.hUR() + readout.yiX()); // below the readout
        if (top >= h) return;
        g.qoU();                                  // save (operator included)
        try {
            g.MRY(0, top, w, h - top); g.lYc();   // clip
            g.cfM(com.bitwig.graphics.IOJ.ODA);   // HSL_HUE
            g.Hnt(RED);
            g.MRY(0, top, w, h - top); g.wGc();
        } finally { g.ucQ(); }                    // restore
    }

    /** Tints one rectangle (the arrange view's volume slider, painted from its parent). */
    static void tintRect(com.bitwig.graphics.SYo g, double x, double y, double w, double h) {
        if (w <= 0 || h <= 0) return;
        g.qoU();
        try {
            g.MRY(x, y, w, h); g.lYc();
            g.cfM(com.bitwig.graphics.IOJ.ODA);   // HSL_HUE
            g.Hnt(RED);
            g.MRY(x, y, w, h); g.wGc();
        } finally { g.ucQ(); }
    }
}
