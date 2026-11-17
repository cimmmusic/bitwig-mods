package com.cimm.mixerminipanels;

import bitwig.obf.eoM; // an OS pointer event (scroll / gesture types epr, eps, epv, epz), window coordinates
import java.util.ArrayList;
import java.util.List;

/**
 * Scrolling over a node: Bitwig routes wheel / trackpad events to its scroll views before our view sees them, so the
 * hooks catch the event where Bitwig receives it. Over a node of a mini view on screen, the event changes the node's Q
 * and Bitwig doesn't handle it; anywhere else Bitwig does as usual.
 */
final class Wheel {
    private Wheel() {}
    private static int logs, calls;

    /** True when the event was ours (used for a node's Q). */
    static boolean consume(eoM e) {
        try {
            double x = e.wGc(), y = e.eLY(); // pointer, window coordinates
            double a = e.fJR(), b = e.iHb(), c = e.nVd(), d = e.ucQ();
            long now = System.nanoTime();
            List<MiniGraph> views = Collapse.liveViews();
            if (calls++ < 12) {
                StringBuilder sb = new StringBuilder("scroll event " + e.getClass().getSimpleName() + (e instanceof bitwig.obf.eph w ? " amount=" + w.FzB() + " " + w.IwV() + " " + w.UNj() : "") + " at " + (int) x + "," + (int) y + " fJR=" + a + " iHb=" + b + " nVd=" + c + " ucQ=" + d + "; views:");
                for (MiniGraph g : views) {
                    com.bitwig.base.geom.OLR o = g.ihs();
                    sb.append(" [").append(o == null ? "?" : o.FzB() + "," + o.hUR()).append(" ").append(g.Sxo()).append("x").append(g.yiX())
                      .append(" painted ").append((now - g.lastPaint) / 1_000_000).append("ms ago]");
                }
                MiniGraph.MOD.log(sb.toString());
            }
            // the most recently drawn view under the pointer (rows are rebuilt often; old copies of a view sit unused)
            MiniGraph best = null; double bx = 0, by = 0;
            for (MiniGraph g : views) {
                if (g.isCollapsed() || g.lastPaint == 0) continue;
                com.bitwig.base.geom.OLR o = g.ihs(); // the view's position in the window
                if (o == null) continue;
                double lx = x - o.FzB(), ly = y - o.hUR();
                if (lx < 0 || ly < 0 || lx > g.Sxo() || ly > g.yiX()) continue;
                if (best == null || g.lastPaint > best.lastPaint) { best = g; bx = lx; by = ly; }
            }
            if (best == null || !best.wantsWheel(bx, by)) return false;  // not over a node: Bitwig scrolls
            double delta;
            if (e instanceof bitwig.obf.eph w) {                          // a wheel / trackpad scroll
                if (!"VERTICAL".equals(w.UNj().name())) return true;      // sideways part of a swipe over a node: ignore
                delta = w.IwV().name().equals("PIXELS") ? w.FzB() / 8.0 : w.FzB(); // trackpad pixels vs wheel steps
            } else delta = d != 0 ? d : c;
            if (logs < 25) { logs++; MiniGraph.MOD.log("scroll on node: delta=" + delta); }
            if (delta != 0) { best.wheelAt(bx, by, delta); best.jbi(); }
            return true;
        } catch (Throwable t) {
            MiniGraph.MOD.log("scroll failed", t);
        }
        return false;
    }
}
