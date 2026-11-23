package com.cimm.clipfx;

import bitwig.obf.dJH; // drag start event
import bitwig.obf.dJM; // drag move event
import bitwig.obf.drX; // a drag in progress
import bitwig.obf.dve; // a widget
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size

/**
 * A drag grip (two columns of dots) at the right of an insert's row in the Inspector: dragging it up or down moves
 * the insert in the clip's chain, one place per row height passed, as the mouse goes.
 */
final class Grip extends dve {
    private static final iok BG = iok.cfM(44, 44, 46), DOT = iok.cfM(130, 130, 136), DOT_ON = iok.cfM(235, 150, 60);
    private final Object device;
    private boolean dragging;

    Grip(Object device) { this.device = device; }

    @Override public SYo mK_() { return new SYo(16, 10); }
    @Override public SYo iG_() { return new SYo(16, 24); }
    @Override public SYo ni_() { return new SYo(16, 100000); }
    @Override public dve MJ() { return this; }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        double w = Sxo(), h = yiX();
        try {
            g.qoU();
            g.MRY(0, 0, w, h); g.lYc();
            g.Hnt(BG); g.MRY(0, 0, w, h); g.wGc();
            g.Hnt(dragging ? DOT_ON : DOT);
            double cy = h / 2;
            for (int r = -1; r <= 1; r++) for (int c = 0; c < 2; c++) { g.MRY(w / 2 - 3 + c * 5, cy + r * 5 - 1, 2, 2); g.wGc(); }
            g.ucQ();
        } catch (Throwable t) { if (fails++ < 2) ClipFx.MOD.log("Clip FX: painting the grip failed", t); }
    }
    private static int fails;

    @Override public drX cfM(eoX button, double x, double y, eoW mods, int clicks) {
        drX d = press(button);
        return d != null ? d : super.cfM(button, x, y, mods, clicks);
    }

    @Override public drX MRY(eoX button, double x, double y, eoW mods, int clicks) {
        drX d = press(button);
        return d != null ? d : super.MRY(button, x, y, mods, clicks);
    }

    private drX press(eoX button) {
        try { return button.name().equals("SELECTION") ? new Drag() : null; } catch (Throwable t) { return null; }
    }

    private final class Drag extends drX {
        private double acc, rowH;
        @Override public void cfM(dJH e) {
            super.cfM(e);
            dragging = true; acc = 0;
            rowH = Math.max(12, yiX());
            jbi();
        }
        @Override public void cfM(dJM e) { // moves: counted, the insert moves on release
            super.cfM(e);
            try { acc += e.uWE(); } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: dragging an insert failed", t); }
        }
        @Override public void cfM(bitwig.obf.dJI e) { // released
            super.cfM(e);
            dragging = false; jbi();
            int by = (int) Math.round(acc / rowH);
            if (by != 0) com.bitwig.flt.app.CHR.Grk().asyncExec(() -> { ClipGate.move(device, by); ClipFxInspector.refreshSoon(); });
        }
        @Override public boolean jQ_() { return false; }
        @Override public boolean jB_() { return true; }
    }
}
