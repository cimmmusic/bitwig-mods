package com.cimm.minimal;

import bitwig.obf.dve;
import bitwig.obf.iok;
import com.bitwig.base.geom.SYo;

/**
 * The small arrow after a menu button's name (File ▾): pointing down, and up while its menu is open. It sits in a row
 * with the button's label, inside the button, so the button's frame (the pill when the menu is open) wraps it too.
 * Its height follows the label's, so it is centred on the text whichever way the row aligns its children.
 * (Overrides of Bitwig's widget methods sit in this direct subclass of dve, as BML requires.)
 */
final class MenuArrow extends dve {
    /** Triangle 8 x 6; 1 unit before it, and RIGHT units after it, matching the space before the name on the left. */
    static final double LEAD = 1, TRI_W = 8, TRI_H = 6, RIGHT = 8, WIDTH = LEAD + TRI_W + RIGHT;
    private final dve label;

    MenuArrow(dve label) { this.label = label; }

    private long height() {
        try { SYo s = label.iG_(); return s == null ? 12 : Math.max(8, s.IwV()); } catch (Throwable t) { return 12; }
    }

    @Override public dve MJ() { return this; }
    @Override public boolean HVx() { return false; }
    @Override public boolean TtY(double x, double y) { return false; }
    @Override public boolean h_(double x, double y) { return false; }
    @Override public SYo mK_() { return new SYo((long) WIDTH, height()); }
    @Override public SYo iG_() { return new SYo((long) WIDTH, height()); }
    @Override public SYo ni_() { return new SYo((long) WIDTH, 100000); }

    private static int logs;

    /** For tuning: the space left of the name and right of the arrow, inside the button (screen units). */
    private String margins() {
        try {
            dve button = this;
            for (int i = 0; i < 6 && button != null && !(button instanceof bitwig.obf.dAd); i++) button = button.RbC();
            com.bitwig.base.geom.OLR b = button == null ? null : button.ihs(), l = label.ihs(), me = ihs();
            if (b == null || l == null || me == null) return "";
            double left = l.FzB() - b.FzB(), right = (b.FzB() + button.Sxo()) - (me.FzB() + LEAD + TRI_W);
            return " | name starts " + Math.round(left) + " in, arrow ends " + Math.round(right) + " from the right";
        } catch (Throwable t) { return ""; }
    }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        try {
            boolean open = Minimal.inOpenAnchor(this);
            double h = yiX(), cx = LEAD + TRI_W / 2, cy = h / 2 + 0.5;
            iok c = Legible.icon(Minimal.arrowColour());
            g.Hnt(c);
            if (open) { g.MRY(cx - TRI_W / 2, cy + TRI_H / 2); g.cfM(cx + TRI_W / 2, cy + TRI_H / 2); g.cfM(cx, cy - TRI_H / 2); }
            else { g.MRY(cx - TRI_W / 2, cy - TRI_H / 2); g.cfM(cx + TRI_W / 2, cy - TRI_H / 2); g.cfM(cx, cy + TRI_H / 2); }
            g.wGc();
            if (logs++ < 2) Minimal.MOD.log("menu arrow drawn " + Math.round(Sxo()) + "x" + Math.round(h) + (open ? " (open)" : "") + margins());
        } catch (Throwable t) {
            if (logs++ < 4) Minimal.MOD.log("menu arrow paint failed", t);
        }
    }
}
