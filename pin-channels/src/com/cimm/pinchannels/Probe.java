package com.cimm.pinchannels;

import bitwig.obf.dve; // a widget

import java.util.List;

/**
 * One-off structure log for building frozen rows: from the arranger panel, every track header widget's chain of
 * parents (class, size, position) up to the panel, and the children of the first scrolling ancestor.
 */
final class Probe {
    private Probe() {}

    static void arranger(dve window) {
        StringBuilder sb = new StringBuilder("PROBE arranger:\n");
        try {
            dve panel = window.lYc(bitwig.obf.aYp.class).findFirst().orElse(null) instanceof dve p ? p : null;
            if (panel == null) { Pins.MOD.log("PROBE: no arranger panel under the window"); return; }
            sb.append("panel ").append(desc(panel)).append('\n');
            // Row 1 = the first small scroll box (a track header's): its chain up to the panel, then every widget
            // in the panel whose top or bottom lines up with that row (header parts, lane, clip area…).
            dve row = null;
            for (Object o : panel.lYc(bitwig.obf.dXk.class).toList()) {
                dve d = (dve) o;
                if (d.yiX() > 20 && d.yiX() < 400 && d.Sxo() > 100) { row = d; break; }
            }
            if (row != null) {
                sb.append("row box chain:\n");
                int depth = 0;
                for (dve p = row; p != null && depth < 25; p = p.RbC(), depth++) {
                    sb.append("   ").append(desc(p)).append(p == panel ? "  <- panel" : "").append('\n');
                    if (p == panel) break;
                }
                long top = row.ihs().hUR(), bottom = top + (long) row.yiX();
                sb.append("widgets lined up with row 1 (y ").append(top).append("..").append(bottom).append("):\n");
                int n = 0;
                for (Object o : panel.lYc(dve.class).toList()) {
                    dve d = (dve) o;
                    com.bitwig.base.geom.OLR at = d.ihs();
                    if (at == null) continue;
                    long y = at.hUR(), y2 = y + (long) d.yiX();
                    boolean lined = (Math.abs(y - top) <= 2 || Math.abs(y2 - bottom) <= 2) && d.yiX() > 10;
                    boolean tall = d.yiX() > 600 && d.Sxo() > 300;                 // big containers: lane areas, scrollers
                    if (lined || tall) {
                        if (n++ >= 80) { sb.append("   …\n"); break; }
                        sb.append("   ").append(desc(d)).append(" parent ").append(d.RbC() == null ? "-" : d.RbC().getClass().getName()).append('\n');
                    }
                }
            }
            sb.append("scroll views (dXk) under panel:\n");
            panel.lYc(bitwig.obf.dXk.class).limit(10).forEach(o -> sb.append("   ").append(desc((dve) o)).append('\n'));
        } catch (Throwable t) { sb.append("failed: ").append(t); }
        Pins.MOD.log(sb.toString());
    }

    private static List<?> children(dve p) {
        try { List<?> l = p.vJT(); if (l != null && !l.isEmpty()) return l; } catch (Throwable ignored) {}
        try { return p.YBm().toList(); } catch (Throwable ignored) {}
        return List.of();
    }

    static String desc(dve w) {
        String pos = "";
        try { com.bitwig.base.geom.OLR o = w.ihs(); if (o != null) pos = " @" + o.FzB() + "," + o.hUR(); } catch (Throwable ignored) {}
        return w.getClass().getName() + " " + (long) w.Sxo() + "x" + (long) w.yiX() + pos;
    }
}
