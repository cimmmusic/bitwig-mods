package com.cimm.mixervca;

import bitwig.obf.dve; // a widget
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size

/**
 * Laid over a VCA's volume section: paints red in "hue" blend mode (HSL_HUE, IOJ.ODA — Bitwig's graphics follow
 * Cairo's operators), so whatever is orange there (the fader's fill and line) turns red at the same brightness, while
 * greys, black and white stay as they are. The dB readout (a text widget, dut) is left out: the overlay starts just
 * below the lowest text near the top. It never takes the mouse (TtY: "is the point inside me" answers no), so the
 * fader underneath works as usual.
 */
final class HueOverlay extends dve {
    static final iok RED = iok.cfM(226, 40, 40);

    private final dve section;

    HueOverlay(dve section) { this.section = section; }

    @Override public SYo mK_() { return new SYo(0, 0); }
    @Override public SYo iG_() { return new SYo(0, 0); }
    @Override public SYo ni_() { return new SYo(100000, 100000); } // fills the stack

    @Override public boolean TtY(double x, double y) { return false; } // never hit: the mouse goes to the fader
    @Override public boolean d_(double x, double y) { return false; }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        double w = Sxo(), h = yiX();
        double top = 0;
        try { // below the readout: the lowest text widget in the top third of the section
            com.bitwig.base.geom.OLR me = ihs();
            if (me != null) {
                for (Object o : (Iterable<Object>) section.lYc(bitwig.obf.dut.class)::iterator) {
                    if (!(o instanceof dve t)) continue;
                    com.bitwig.base.geom.OLR p = t.ihs();
                    if (p == null) continue;
                    double bottom = (p.hUR() - me.hUR()) + t.yiX();
                    if (p.hUR() - me.hUR() < h / 3 && bottom > top) top = bottom;
                }
            }
        } catch (Throwable ignored) {}
        if (top >= h) return;
        try {
            g.qoU();                                  // save (operator included)
            g.MRY(0, top, w, h - top); g.lYc();       // clip
            g.cfM(com.bitwig.graphics.IOJ.ODA);       // HSL_HUE
            g.Hnt(RED);
            g.MRY(0, top, w, h - top); g.wGc();
            g.ucQ();                                  // restore
        } catch (Throwable t) {
            try { g.ucQ(); } catch (Throwable ignored) {}
        }
    }

    @Override public dve MJ() { return this; }
}
