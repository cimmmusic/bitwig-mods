package com.cimm.mixerminipanels;

import bitwig.obf.eoM; // an OS pointer event (scroll / gesture types epr, eps, epv, epz), window coordinates

/**
 * Scrolling over a node: Bitwig routes wheel / trackpad events to its scroll views before our view sees them, so the
 * hooks catch the event where Bitwig receives it. Over a node of a mini view on screen, the event changes the node's Q
 * and Bitwig doesn't handle it; anywhere else Bitwig does as usual.
 *
 * Every scroll in Bitwig passes through here, so it stays cheap: with no mini view on screen it returns at once, and
 * otherwise it only tests the on-screen views' rectangles (no allocation, no logging).
 */
final class Wheel {
    private Wheel() {}
    private static boolean loggedFailure;

    /** True when the event was ours (used for a node's Q). */
    static boolean consume(eoM e) {
        MiniGraph[] views = Poll.views(); // only views attached to the window
        if (views.length == 0) return false;
        try {
            double x = e.wGc(), y = e.eLY(); // pointer, window coordinates
            // the most recently drawn view under the pointer
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
            } else {
                double c = e.nVd(), d = e.ucQ();
                delta = d != 0 ? d : c;
            }
            if (delta != 0) { best.wheelAt(bx, by, delta); best.jbi(); }
            return true;
        } catch (Throwable t) {
            if (!loggedFailure) { loggedFailure = true; MiniGraph.MOD.log("scroll failed", t); }
        }
        return false;
    }
}
