package com.cimm.minimal;

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
            iok c = Minimal.dimColour(window);
            if (c == null) return;
            g.Hnt(c);
            g.MRY(0, 0, Sxo(), yiX());
            g.wGc();
            raised(g);
        } catch (Throwable ignored) {}
    }

    /** The open menu's button, painted again over the dim where it sits in this window. */
    private static int raiseLogs;

    private void raised(com.bitwig.graphics.SYo g) {
        com.bitwig.base.geom.OLR me = ihs();
        if (me == null) return;
        bitwig.obf.dve myRoot = root(this);
        for (bitwig.obf.dve a : Minimal.openAnchors()) {
            try {
                if (root(a) != myRoot) continue; // another window
                // a menu button (dLh) wraps the button itself (its gfw()): that is what draws the name
                bitwig.obf.dve w = a instanceof bitwig.obf.dLh m && m.gfw() != null ? m.gfw() : a;
                com.bitwig.base.geom.OLR r = w.ihs();
                if (r == null) continue;
                g.qoU();
                try {
                    g.FzB(r.FzB() - me.FzB(), r.hUR() - me.hUR());
                    // only what the button draws on top of its background: its name (in the highlight colour while
                    // its menu is open) and any icon; not the pressed background behind it
                    w.cfM(g);
                    w.b_(g);
                } finally { g.ucQ(); }
                if (raiseLogs++ < 2) Minimal.MOD.log("menu name drawn above the dim: " + w.getClass().getName());
            } catch (Throwable t) { if (raiseLogs++ < 4) Minimal.MOD.log("drawing the menu name above the dim failed", t); }
        }
    }

    private static bitwig.obf.dve root(bitwig.obf.dve w) {
        bitwig.obf.dve r = w;
        for (bitwig.obf.dve p = w.RbC(); p != null; p = p.RbC()) r = p;
        return r;
    }
}
