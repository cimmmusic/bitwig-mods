package com.cimm.groove;

import bitwig.obf.dJH; // drag start event
import bitwig.obf.dJM; // drag move event
import bitwig.obf.drX; // a drag in progress
import bitwig.obf.dve; // a widget
import bitwig.obf.eoW; // modifier keys
import bitwig.obf.eoX; // mouse button
import bitwig.obf.iok; // a colour
import com.bitwig.base.geom.SYo; // a size

/**
 * The Manual groove: one bar, a line per Base step. A line's position is the step's timing (drag left/right, up to half a
 * step either way), its height the step's velocity (drag up/down). Double-click a line to put it back.
 *
 * Drawn in the mini views' house style (Mixer Device Mini Panels): dark panel, minimal grid, 2-unit black edges, white
 * dot nodes (radius 5); the groove's lines in Bitwig's orange.
 */
final class Graph extends dve {
    static final double H = 230, TOP = 30, BOTTOM = 8, PAD = 10;
    static final double FONT_SHIFT = 0; // beat numbers sit on their baseline at the translated origin

    // the mini views' colours
    static final iok FIELD = iok.cfM(30, 30, 32);
    static final iok STEP = iok.cfM(46, 46, 50);
    static final iok BEAT = iok.cfM(66, 66, 72);
    static final iok EDGE = iok.cfM(0, 0, 0);
    static final iok NODE = iok.cfM(245, 245, 245);
    static final iok NUMBER = iok.cfM(110, 110, 118);
    // the groove's lines: Bitwig's orange (lighter while dragged)
    static final iok LINE = Widgets.ORANGE;
    static final iok LINE_HOT = iok.cfM(255, 184, 120);
    static final iok LINE_DIM = new iok(239 / 255f, 141 / 255f, 57 / 255f, 0.45f);
    static final iok NODE_DIM = new iok(0.96f, 0.96f, 0.96f, 0.55f);

    private int hot = -1;          // the step being dragged
    private double heldOff, heldVel;
    private long lastPress;
    private int lastPressStep = -1;
    /** Timing snaps (as the mini views' sticky lines) at these points of a step: catches within SNAP units of one, lets
     *  go past RELEASE. */
    static final double SNAP = 4, RELEASE = 8;
    static final double[] SNAPS = {-0.5, -0.25, 0, 0.25, 0.5};
    private double snapped = Double.NaN; // the timing point the grabbed node is held at, or NaN
    /** Velocity snaps too, the same way, to the 100 % line (127), which the graph draws. */
    static final double[] VEL_SNAPS = {127};
    static final iok FULL_LINE = iok.cfM(96, 96, 104);
    private double velSnapped = Double.NaN;
    private long hotUntil;

    /** In the Groove panel the graph takes all the height it is given; in the Play menu it has a fixed height. */
    final boolean fill;
    Graph() { this(false); }
    Graph(boolean fill) { this.fill = fill; }

    /** The graphs and buttons in Groove panels (weakly held): told to update after any change, wherever it was made. */
    static final java.util.Set<Graph> panels = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static final java.util.Set<Native.Value> panelValues = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static final java.util.Set<dve> panelKnobs = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static final java.util.Set<Native.Chooser> choosers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /** Shown/hidden parts that follow a value (the Quantize window's tabs): each holds its own check. */
    static final java.util.Map<dve, java.util.function.BooleanSupplier> shownWhen = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    static void refreshPanels() {
        for (Graph g : panels.toArray(new Graph[0])) try { g.jbi(); } catch (Throwable ignored) {}
        for (Native.Value v : panelValues.toArray(new Native.Value[0])) try { v.fire(); } catch (Throwable ignored) {}
        for (dve k : panelKnobs.toArray(new dve[0])) try { k.jbi(); } catch (Throwable ignored) {}
        for (Native.Chooser c : choosers.toArray(new Native.Chooser[0])) try { c.refresh(); } catch (Throwable ignored) {}
        java.util.List<dve> relayout = new java.util.ArrayList<>();
        synchronized (shownWhen) {
            for (var e : shownWhen.entrySet()) try { e.getKey().t_(e.getValue().getAsBoolean()); relayout.add(e.getKey()); } catch (Throwable ignored) {}
        }
        for (dve w : relayout) try { for (dve p = w; p != null; p = p.RbC()) p.oQi(); } catch (Throwable ignored) {}
    }

    @Override public SYo mK_() { return new SYo(300, fill ? 120 : (long) H); }
    @Override public SYo iG_() { return new SYo(560, fill ? 300 : (long) H); }
    @Override public SYo ni_() { return new SYo(100000, fill ? 100000 : (long) H); }
    @Override public dve MJ() { return this; }

    private int base() { return State.manualAmounts.base; }
    private double stepW(double w, int n) { return (w - 2 * PAD) / n; }
    private double lineX(double w, int n, int k, double off) { return PAD + (k + off) * stepW(w, n) + 3; }
    private double plotH(double h) { return h - TOP - BOTTOM; }

    @Override public void Hnt(com.bitwig.graphics.SYo g) {
        super.Hnt(g);
        try {
            g.qoU();
            double w = Sxo(), h = yiX();
            g.MRY(0, 0, w, h); g.lYc();
            paint(g, w, h);
        } catch (Throwable t) { State.MOD.log("graph paint failed", t); }
        finally { try { g.ucQ(); } catch (Throwable ignored) {} }
    }

    private void paint(com.bitwig.graphics.SYo g, double w, double h) {
        g.Hnt(FIELD); g.MRY(0, 0, w, h); g.wGc();
        int base = base();
        double[][] m = State.manual(base);
        int n = m[0].length;
        double sw = stepW(w, n), beatW = (w - 2 * PAD) / State.BAR;
        // dashed step lines, then solid beat lines with their numbers
        g.IwV(1);
        for (int k = 0; k < n; k++) {
            double x = Math.round(PAD + k * sw) + 0.5;
            if (Math.abs((k * Groove.BASES[base]) % 1) < 1e-6) continue; // a beat line goes here
            g.Hnt(STEP);
            for (double y = 3; y < h - 3; y += 8) { g.MRY(x, y); g.cfM(x, Math.min(h - 3, y + 4)); }
            g.hbP();
        }
        for (int b = 0; b < State.BAR; b++) {
            double x = Math.round(PAD + b * beatW) + 0.5;
            if (b > 0) { g.Hnt(BEAT); g.MRY(x, 0); g.cfM(x, h); g.hbP(); }
            g.qoU(); // beat numbers, small
            try { g.FzB(x + 8, 18); g.Hnt(0.7, 0.7); Widgets.text(g, Integer.toString(b + 1), 0, -FONT_SHIFT, NUMBER); } finally { g.ucQ(); }
        }
        // the 100 % velocity line (nodes snap to it)
        double ph = plotH(h);
        double full = h - BOTTOM - ph;
        g.Hnt(FULL_LINE); g.IwV(1);
        for (double x = 2; x < w - 2; x += 8) { g.MRY(x, full + 0.5); g.cfM(Math.min(w - 2, x + 4), full + 0.5); }
        g.hbP();
        g.qoU();
        try { g.FzB(w - 44, full - 4); g.Hnt(0.6, 0.6); Widgets.text(g, "100%", 0, 0, NUMBER); } finally { g.ucQ(); }
        // the steps
        java.util.Set<Integer> sel = selection();
        boolean any = !sel.isEmpty();
        for (int k = 0; k < n; k++) { // lines: selected ones thick and bright; the rest dimmed while something is selected
            double x = lineX(w, n, k, m[0][k]);
            double top = h - BOTTOM - m[1][k] / 127.0 * ph;
            boolean on = sel.contains(k);
            g.IwV(on ? 4.5 : 3);
            g.Hnt(on ? LINE_HOT : any ? LINE_DIM : LINE);
            g.MRY(x, h - BOTTOM); g.cfM(x, top); g.hbP();
        }
        for (int k = 0; k < n; k++) { // nodes on top: the handles
            double x = lineX(w, n, k, m[0][k]);
            double top = h - BOTTOM - m[1][k] / 127.0 * ph;
            if (sel.contains(k)) { // selected: bigger, orange inside a white ring
                Widgets.disc(g, x, top, 8, NODE);
                Widgets.disc(g, x, top, 6, k == hot ? LINE_HOT : LINE);
            } else Widgets.disc(g, x, top, 5, any ? NODE_DIM : NODE);
        }
        if (band != null) { // the selection box
            double x0 = Math.min(band[0], band[2]), y0 = Math.min(band[1], band[3]);
            double bw = Math.abs(band[2] - band[0]), bh = Math.abs(band[3] - band[1]);
            g.Hnt(new iok(1f, 1f, 1f, 0.08f)); g.MRY(x0, y0, bw, bh); g.wGc();
            g.Hnt(new iok(1f, 1f, 1f, 0.45f)); g.IwV(1); g.MRY(x0 + 0.5, y0 + 0.5, bw - 1, bh - 1); g.hbP();
        }
        g.Hnt(EDGE); g.MRY(0, 0, 2, h); g.wGc(); g.MRY(w - 2, 0, 2, h); g.wGc(); // 2-unit black edges
        // a message (couldn't save / nothing to copy), else while dragging the step's timing and velocity, top right
        String msg = State.message;
        if (msg == null && State.manualAmounts.strength == 0) msg = "Strength 0%: no groove";
        else if (msg == null && State.manualAmounts.timing == 0 && State.manualAmounts.velocity == 0) msg = "Timing and Velocity 0%: no groove";
        else if (msg == null && State.manualAmounts.timing == 0) msg = "Timing 0%: no timing change";
        if (msg != null) Widgets.rightAligned(g, Widgets.fit(g, msg, w - 140), w - 10, 16, Widgets.WARN);
        else if (sel.size() > 1 && !(hot >= 0 && System.currentTimeMillis() < hotUntil)) {
            Widgets.rightAligned(g, sel.size() + " selected", w - 10, 16, NUMBER);
        }
        else if (hot >= 0 && hot < n && System.currentTimeMillis() < hotUntil) {
            String s = "Step " + (hot + 1) + "   " + signed(Math.round(m[0][hot] * 100)) + "%   Velocity " + Math.round(m[1][hot]);
            Widgets.rightAligned(g, s, w - 10, 16, NUMBER);
        }
    }

    private static String signed(long v) { return v > 0 ? "+" + v : Long.toString(v); }

    // ---- selection ----
    private final java.util.Set<Integer> selected = new java.util.TreeSet<>();
    private int selectedBase = -1;
    private double[] band; // x0, y0, x1, y1 while dragging a selection box

    private java.util.Set<Integer> selection() {
        if (selectedBase != base()) { selected.clear(); selectedBase = base(); }
        return selected;
    }

    private double nodeY(double h, double vel) { return h - BOTTOM - vel / 127.0 * plotH(h); }

    /** The step whose line or node is under (x, y) (nearest within 12 units across, from the node down), or -1. */
    private int stepAt(double x, double y) {
        double w = Sxo(), h = yiX();
        double[][] m = State.manual(base());
        int n = m[0].length, best = -1;
        double bestD = 12;
        for (int k = 0; k < n; k++) {
            double d = Math.abs(x - lineX(w, n, k, m[0][k]));
            if (d < bestD && y >= nodeY(h, m[1][k]) - 12) { bestD = d; best = k; }
        }
        return best;
    }

    /** The step whose line is nearest to x (within 10 units), or -1. */
    private int stepNear(double x) {
        double w = Sxo();
        double[][] m = State.manual(base());
        int n = m[0].length, best = -1;
        double bestD = 10;
        for (int k = 0; k < n; k++) { double d = Math.abs(x - lineX(w, n, k, m[0][k])); if (d < bestD) { bestD = d; best = k; } }
        return best;
    }

    /** Shift held during a drag move (read from the keys' text, which survives renames). */
    static boolean shift(bitwig.obf.dJM e) {
        try { return String.valueOf(e.E3()).contains("Shift"); } catch (Throwable t) { return false; }
    }

    /** Shift or Cmd held: add to / take from the selection (read from the keys' text, which survives renames). */
    private static boolean additive(eoW mods) {
        try { String s = String.valueOf(mods); return s.contains("Shift") || s.contains("Cmd"); } catch (Throwable t) { return false; }
    }

    /** Bitwig only opens a widget's own right-click menu when it says it has one. */
    @Override public boolean iZ_() { return true; }

    /** Right-click: Reset Step / Reset Selected Steps / Reset Graph, in Bitwig's popup menu. */
    @Override public void cfM(bitwig.obf.dKW popup, double x, double y, eoW mods) {
        try {
            int k = stepAt(x, y);
            java.util.Set<Integer> sel = selection();
            if (k >= 0 && !sel.contains(k)) popup.cfM("Reset Step " + (k + 1), (Runnable) () -> resetSteps(java.util.List.of(k)));
            if (!sel.isEmpty()) popup.cfM("Reset Selected Steps", (Runnable) () -> resetSteps(new java.util.ArrayList<>(sel)));
            popup.cfM("Reset Graph", (Runnable) () -> { State.manualReset(); jbi(); });
        } catch (Throwable t) { State.MOD.log("graph menu failed", t); }
    }

    private void resetSteps(java.util.Collection<Integer> steps) {
        double[][] m = State.manual(base());
        State.manualWillChange();
        for (int k : steps) if (k < m[0].length) { m[0][k] = 0; m[1][k] = 100; }
        State.manualChanged();
        jbi();
    }

    @Override public drX cfM(eoX button, double x, double y, eoW mods, int clicks) {
        drX d = press(button, x, y, mods, clicks);
        return d != null ? d : super.cfM(button, x, y, mods, clicks);
    }

    @Override public drX MRY(eoX button, double x, double y, eoW mods, int clicks) {
        drX d = press(button, x, y, mods, clicks);
        return d != null ? d : super.MRY(button, x, y, mods, clicks);
    }

    private static int pressLogs;
    private drX press(eoX button, double x, double y, eoW mods, int clicks) {
        try {
            if (!button.name().equals("SELECTION")) return null;
            boolean add = additive(mods);
            int k = stepAt(x, y);
            if (k < 0) k = stepNear(x); // forgiving: the nearest line, wherever on it
            if (pressLogs++ < 12) State.MOD.log("graph press at " + Math.round(x) + "," + Math.round(y) + " of " + Sxo() + "x" + yiX()
                    + " -> step " + k + (add ? " (add)" : ""));
            return k >= 0 ? moveNodes(k, add, clicks) : selectBox(x, y, add);
        } catch (Throwable t) { State.MOD.log("graph press failed", t); return null; }
    }

    /** Pressing a node: select it (Shift/Cmd: toggle), then drag every selected node together. */
    private drX moveNodes(int k, boolean add, int clicks) {
        return new drX() {
            final java.util.Map<Integer, double[]> held = new java.util.HashMap<>(); // step -> {offset, velocity}
            boolean moving;
            @Override public void cfM(dJH e) {
                super.cfM(e);
                java.util.Set<Integer> sel = selection();
                double[][] m = State.manual(base());
                if (k >= m[0].length) return;
                if (add) { if (!sel.remove(k)) sel.add(k); }
                else if (!sel.contains(k)) { sel.clear(); sel.add(k); }
                moving = sel.contains(k);
                hot = k; hotUntil = System.currentTimeMillis() + 1500;
                State.manualWillChange(); // one undo step per drag (or double-click)
                if (false) { for (int s : sel) if (s < m[0].length) { m[0][s] = 0; m[1][s] = 100; } State.manualChanged(); }
                for (int s : sel) if (s < m[0].length) held.put(s, new double[]{m[0][s], m[1][s]});
                snapped = Double.NaN;
                for (double p : SNAPS) if (Math.abs(m[0][k] - p) < 1e-9) snapped = p;
                velSnapped = Double.NaN;
                for (double p : VEL_SNAPS) if (Math.abs(m[1][k] - p) < 1e-9) velSnapped = p;
                jbi();
            }
            boolean moved;
            /** Released without dragging: a click; two on the same step within 400 ms put it (or the selection) back. */
            @Override public void cfM(bitwig.obf.dJI e) {
                super.cfM(e);
                if (moved) return;
                long now = System.currentTimeMillis();
                boolean dbl = now - lastPress < 400 && lastPressStep == k;
                lastPress = dbl ? 0 : now; lastPressStep = k;
                if (!dbl) return;
                double[][] m = State.manual(base());
                State.manualWillChange();
                java.util.Set<Integer> sel = selection();
                if (sel.isEmpty()) sel.add(k);
                for (int s : sel) if (s < m[0].length) { m[0][s] = 0; m[1][s] = 100; }
                State.manualChanged();
                jbi();
            }
            @Override public void cfM(dJM e) {
                super.cfM(e);
                moved = true;
                if (!moving) return;
                try {
                    double[][] m = State.manual(base());
                    boolean shiftHeld = shift(e);
                    double fine = shiftHeld ? 0.1 : 1; // Shift: a tenth of the speed, and no snapping
                    double sw = stepW(Sxo(), m[0].length), dx = e.ejd() / sw * fine, dv = -e.uWE() / plotH(yiX()) * 127 * fine;
                    for (double[] hv : held.values()) { hv[0] += dx; hv[1] += dv; }
                    // the grabbed node's timing snaps at -50/-25/0/+25/+50 % of a step; the others keep their distance to it
                    double[] anchor = held.get(k);
                    double shift = 0;
                    if (shiftHeld) { snapped = Double.NaN; velSnapped = Double.NaN; }
                    else if (anchor != null) {
                        if (!Double.isNaN(snapped) && Math.abs(anchor[0] - snapped) * sw > RELEASE) snapped = Double.NaN;
                        if (Double.isNaN(snapped)) for (double p : SNAPS) if (Math.abs(anchor[0] - p) * sw < SNAP) { snapped = p; break; }
                        if (!Double.isNaN(snapped)) shift = snapped - anchor[0];
                    }
                    // the grabbed node's velocity snaps to the 100 % line; the others keep their distance to it
                    double vshift = 0, perVel = plotH(yiX()) / 127; // units per velocity step
                    if (anchor != null && !shiftHeld) {
                        if (!Double.isNaN(velSnapped) && Math.abs(anchor[1] - velSnapped) * perVel > RELEASE) velSnapped = Double.NaN;
                        if (Double.isNaN(velSnapped)) for (double p : VEL_SNAPS) if (Math.abs(anchor[1] - p) * perVel < SNAP) { velSnapped = p; break; }
                        if (!Double.isNaN(velSnapped)) vshift = velSnapped - anchor[1];
                    }
                    for (java.util.Map.Entry<Integer, double[]> en : held.entrySet()) {
                        int s = en.getKey();
                        if (s >= m[0].length) continue;
                        double off = Math.max(-0.5, Math.min(0.5, en.getValue()[0] + shift));
                        m[0][s] = s == k && !Double.isNaN(snapped) ? snapped : Math.round(off * 1000) / 1000.0; // tenth-percent steps
                        m[1][s] = s == k && !Double.isNaN(velSnapped) ? velSnapped
                                : Math.max(1, Math.min(127, Math.round(en.getValue()[1] + vshift)));
                    }
                    hot = k; hotUntil = System.currentTimeMillis() + 1500;
                    State.manualChanged();
                } catch (Throwable t) { State.MOD.log("graph drag failed", t); }
                jbi();
            }
            @Override public boolean jQ_() { return false; }
            @Override public boolean jB_() { return true; }
        };
    }

    /** Pressing empty space: drag a box to select the nodes in it (Shift/Cmd: add to the selection); a click clears. */
    private drX selectBox(double x, double y, boolean add) {
        return new drX() {
            java.util.Set<Integer> before;
            @Override public void cfM(dJH e) {
                super.cfM(e);
                before = add ? new java.util.TreeSet<>(selection()) : new java.util.TreeSet<>();
                selection().clear(); selection().addAll(before);
                band = new double[]{x, y, x, y};
                jbi();
            }
            @Override public void cfM(dJM e) {
                super.cfM(e);
                if (band == null) return;
                band[2] += e.ejd(); band[3] += e.uWE();
                double x0 = Math.min(band[0], band[2]), x1 = Math.max(band[0], band[2]);
                double y0 = Math.min(band[1], band[3]), y1 = Math.max(band[1], band[3]);
                double w = Sxo(), h = yiX();
                double[][] m = State.manual(base());
                java.util.Set<Integer> sel = selection();
                sel.clear(); sel.addAll(before);
                for (int s = 0; s < m[0].length; s++) {
                    double nx = lineX(w, m[0].length, s, m[0][s]), ny = nodeY(h, m[1][s]);
                    if (nx >= x0 && nx <= x1 && ny >= y0 && ny <= y1) sel.add(s);
                }
                jbi();
            }
            @Override public void cfM(bitwig.obf.dJI e) { super.cfM(e); band = null; jbi(); } // released
            @Override public boolean jQ_() { return false; }
            @Override public boolean jB_() { return true; }
        };
    }
}
