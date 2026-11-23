package com.cimm.clipfx;

import bitwig.obf.dve;
import bitwig.obf.iok;
import com.bitwig.base.geom.SYo;

/** A thin gap between two insert rows: lit (orange) where a dragged insert will land. */
final class DropLine extends dve {
    private static final iok ON = iok.cfM(235, 150, 60);
    boolean lit;

    @Override public SYo mK_() { return new SYo(10, 3); }
    @Override public SYo iG_() { return new SYo(100, 3); }
    @Override public SYo ni_() { return new SYo(100000, 3); }
    @Override public dve MJ() { return this; }

    void light(boolean on) { if (lit != on) { lit = on; jbi(); } }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        if (!lit) return;
        try { g.qoU(); g.Hnt(ON); g.MRY(0, 0, Sxo(), yiX()); g.wGc(); g.ucQ(); } catch (Throwable ignored) {}
    }
}
