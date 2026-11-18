package com.cimm.mixervca;

import bitwig.obf.dve; // a widget
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size (width, height)

/** Layout probe: a lighter-grey cell with a label, as wide as a narrow strip, as tall as its row allows. */
final class ProbeCell extends dve {
    static final iok FILL = iok.cfM(72, 72, 76);   // lighter than Bitwig's strip grey, not much
    static final iok EDGE = iok.cfM(96, 96, 102);
    static final iok TEXT = iok.cfM(200, 200, 205);
    static final long WIDTH = 56;

    final String label;

    ProbeCell(String label) { this.label = label; }

    @Override public SYo mK_() { return new SYo(WIDTH, 4); }       // minimum
    @Override public SYo iG_() { return new SYo(WIDTH, 16); }      // preferred
    @Override public SYo ni_() { return new SYo(WIDTH, 100000); }  // maximum: fill the row's height

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        double w = Sxo(), h = yiX();
        try {
            g.qoU();
            g.MRY(0, 0, w, h); g.lYc();
            g.Hnt(FILL); g.MRY(1, 1, w - 2, h - 2); g.wGc();
            g.Hnt(EDGE); g.IwV(1); g.MRY(1.5, 1.5, w - 3, h - 3); g.hbP();
            if (h >= 12) g.cfM(4, Math.min(h - 3, 13), label, TEXT);
            g.ucQ();
        } catch (Throwable ignored) {}
    }

    @Override public dve MJ() { return this; }
}
