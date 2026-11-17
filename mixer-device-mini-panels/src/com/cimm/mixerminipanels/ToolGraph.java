package com.cimm.mixerminipanels;

import bitwig.obf.iok; // a colour
import bitwig.obf.jsd; // a device

/**
 * Mini view for Bitwig's "Tool": the stereo image, minimal (no text), on the compressor views' black field.
 *  • A soft band: its centre is the Pan, its length the Width, its height in the view the Gain (0 dB on the faint
 *    middle line).
 *  • White node at the band's centre: left/right = Pan (sticky at the centre line), up/down = Gain. Small dots at the ends: drag them out / in
 *    for Width. Everything with undo.
 * Tool's gain is CONTENTS/AMPLITUDE (dB, from the mixer-pre-gain mod); pan and width are found by name and logged.
 */
final class ToolGraph extends MiniGraph {
    static final iok FIELD = CompGraph.FIELD, GRIDC = CompGraph.GRIDC, MAJOR = iok.cfM(60, 60, 66);
    static final iok BAND = iok.cfM(150, 160, 215);   // lilac, as the compressor curves
    static final iok BAND_FILL = iok.cfM(48, 50, 70);

    final Params.P gain, pan, width;
    static final double SNAP = 10, RELEASE = 16; // px: the centre line catches the pan within SNAP, lets go past RELEASE

    ToolGraph(jsd device) {
        super(device, 110);
        gain = params.findAny("contents/amplitude", "amplitude", "gain", "volume");
        pan = params.findAny("contents/pan", "pan");
        width = params.findAny("contents/width", "width");
        MOD.log("Tool view: gain=" + id(gain) + " pan=" + id(pan) + " width=" + id(width));
    }

    static String id(Params.P p) { return p == null ? "none" : p.id() + " [" + p.min() + ", " + p.max() + "] = " + p.value(); }

    /** Pan as −1 (left) … +1 (right). */
    double panPos() { return pan == null ? 0 : clamp(pan.norm(), 0, 1) * 2 - 1; }
    /** Width as 0 (mono) … 1 (full); a range past 100 % (e.g. 0…2) shows 100 % at half the view. */
    double widthPos() {
        if (width == null) return 0.5;
        double v = width.value();
        if (width.max() > 1.5 || width.max() > 100) return clamp(width.norm(), 0, 1); // e.g. 0…200 %
        return clamp(v, 0, 1);
    }
    /** Gain as 0 (bottom) … 1 (top), with 0 dB in the middle when the range is symmetric. */
    double gainPos() { return gain == null ? 0.5 : clamp(gain.norm(), 0, 1); }

    double cx(double w) { return w / 2 + panPos() * (w / 2 - 8); }
    double half(double w) { return Math.max(2, widthPos() * (w / 2 - 8)); }
    double cy(double h) { return h - 8 - gainPos() * (h - 16); }

    @Override void paintGraph(com.bitwig.graphics.SYo g, double w, double h) {
        g.Hnt(FIELD); g.MRY(0, 0, w, h); g.wGc();
        g.IwV(1);
        g.Hnt(GRIDC);                                             // quarters
        for (int i = 1; i < 4; i++) { double x = Math.floor(w * i / 4) + 0.5; g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
        double y0 = Math.floor(h - 8 - 0.5 * (h - 16)) + 0.5;     // middle: 0 dB
        g.Hnt(MAJOR); g.MRY(0, y0); g.cfM(w, y0); g.hbP();
        double xc = Math.floor(w / 2) + 0.5;                       // centre
        g.MRY(xc, 0); g.cfM(xc, h); g.hbP();

        double c = cx(w), r = half(w), y = cy(h);
        g.Hnt(BAND_FILL); g.MRY(c - r, y - 4, 2 * r, 8); g.wGc();  // the image
        g.Hnt(BAND); g.IwV(2); g.MRY(c - r, y); g.cfM(c + r, y); g.hbP();
        g.MRY(c - r, y, 2.5); g.wGc(); g.MRY(c + r, y, 2.5); g.wGc(); // width handles
        g.Hnt(NODE); g.MRY(c, y, 5); g.wGc();                       // pan / gain node
    }

    @Override Dragger press(double x, double y, double w, double h, int clicks) {
        double c = cx(w), r = half(w), yy = cy(h);
        final Object undo = new Object();
        if (Math.hypot(x - c, y - yy) <= 10) { // the node: pan and gain
            final double[] pos = {c, yy};
            final boolean[] held = {Math.abs(c - w / 2) < 1}; // starts centred: held at the centre line
            return (ddx, ddy) -> {
                pos[0] += ddx; pos[1] += ddy;
                if (pan != null) {
                    // The centre line is sticky: within SNAP px the pan is exactly centre; once held there it only
                    // lets go when the pointer is pulled past RELEASE px, so gain drags don't pan by accident.
                    double off = pos[0] - w / 2;
                    if (Math.abs(off) < SNAP) held[0] = true;
                    else if (Math.abs(off) > RELEASE) held[0] = false;
                    double n = held[0] ? 0.5 : clamp((off / (w / 2 - 8) + 1) / 2, 0, 1);
                    set(pan, pan.min() + (pan.max() - pan.min()) * n, undo, "Change pan");
                }
                if (gain != null) set(gain, gain.min() + (gain.max() - gain.min()) * clamp((h - 8 - pos[1]) / (h - 16), 0, 1), undo, "Change gain");
            };
        }
        if (width != null && Math.abs(y - yy) <= 10 && (Math.abs(x - (c - r)) <= 8 || Math.abs(x - (c + r)) <= 8)) { // an end: width
            final boolean right = x > c;
            final double[] edge = {right ? c + r : c - r};
            return (ddx, ddy) -> {
                edge[0] += ddx;
                double hw = clamp(Math.abs(edge[0] - c) / (w / 2 - 8), 0, 1); // 0…1 of the view
                double v = width.max() > 1.5 ? width.min() + (width.max() - width.min()) * hw : hw;
                set(width, clamp(v, width.min(), width.max()), undo, "Change width");
            };
        }
        return (ddx, ddy) -> {};
    }
}
