package com.cimm.backdrop;

import bitwig.obf.iok;

/**
 * The dim over a window while a menu is open: a widget on the window's layer stack, painting the backdrop colour over
 * its whole area. It takes no part in mouse hit-testing (HVx/TtY/h_ false), so clicks go to what is under it.
 * (Overrides of Bitwig's methods sit in this direct subclass of the widget class, as BML requires.)
 */
final class DimLayer extends bitwig.obf.dve {
    private final bitwig.obf.duG window;

    DimLayer(bitwig.obf.duG window) { this.window = window; }

    @Override public boolean HVx() { return false; }
    @Override public bitwig.obf.dve MJ() { return this; }
    @Override public boolean TtY(double x, double y) { return false; }
    @Override public boolean h_(double x, double y) { return false; }

    @Override public com.bitwig.base.geom.SYo mK_() { return new com.bitwig.base.geom.SYo(0, 0); }
    @Override public com.bitwig.base.geom.SYo iG_() { return new com.bitwig.base.geom.SYo(0, 0); }
    @Override public com.bitwig.base.geom.SYo ni_() { return new com.bitwig.base.geom.SYo(1L << 30, 1L << 30); }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        try {
            iok c = Backdrop.dimColour(window);
            if (c == null) return;
            g.Hnt(c);
            g.MRY(0, 0, Sxo(), yiX());
            g.wGc();
        } catch (Throwable ignored) {}
    }
}
