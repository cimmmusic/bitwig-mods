package com.cimm.groove;

import bitwig.obf.dKB; // a menu-items provider
import bitwig.obf.dWb; // a box layout
import bitwig.obf.dut; // a text label
import bitwig.obf.dve; // a widget

/**
 * Bitwig's Quantize… window with three tabs: Quantize (Bitwig's own settings, unchanged), Groove File and Manual.
 * OK does what the open tab says: Bitwig's quantize, or the groove written into the selected notes (one Undo step).
 * The groove chooser and the Manual graph are the same as the Play menu's: picking or drawing in one shows in the other.
 * Committing doesn't switch playback grooving on.
 */
final class QuantizeTabs {
    private QuantizeTabs() {}

    static final int QUANTIZE = 0, FILE = 1, MANUAL = 2;
    /** The open tab, remembered between openings. */
    static volatile int tab = QUANTIZE;

    static dve build(dve bitwigPanel) {
        try {
            State.load();
            dWb tabs = row();
            String[] names = {"Quantize", "Groove File", "Manual"};
            for (int i = 0; i < 3; i++) {
                int t = i;
                Native.Bool on = new Native.Bool(() -> tab == t, () -> { tab = t; State.repaint(); });
                Graph.panelValues.add(on);
                if (i > 0) tabs.Hnt(2);
                tabs.HL(Native.option(names[i], i == 0 ? 110 : 120, on));
            }

            // Groove File: the chooser, ◀ ▶ Browse, and its knobs
            Native.Chooser chooser = new Native.Chooser();
            chooser.refresh();
            Graph.choosers.add(chooser);
            try { chooser.cfM((dKB) Ui::fill); } catch (Throwable t) { State.MOD.log("quantize chooser menu failed", t); }
            dWb pick = row();
            pick.HL(chooser);
            pick.Hnt(8);
            pick.HL(Native.action("◀", 38, () -> State.step(-1)));
            pick.Hnt(6);
            pick.HL(Native.action("▶", 38, () -> State.step(1)));
            pick.Hnt(6);
            pick.HL(Native.action("Browse", 96, Ui::browse));
            dWb fileKnobs = row();
            knob(fileKnobs, "Strength", 0, 1, 1, () -> State.fileAmounts.strength, v -> State.fileAmounts.strength = v);
            knob(fileKnobs, "Timing", 0, 1, 1, () -> State.fileAmounts.timing, v -> State.fileAmounts.timing = v);
            knob(fileKnobs, "Velocity", -1, 1, 0, () -> State.fileAmounts.velocity, v -> State.fileAmounts.velocity = v);
            knob(fileKnobs, "Random", 0, 1, 0, () -> State.fileAmounts.random, v -> State.fileAmounts.random = v);
            dWb fileBox = dWb.MRY(14, new dve[]{pick, fileKnobs});

            // Manual: the graph and its knobs
            Graph graph = new Graph(false);
            Graph.panels.add(graph);
            dWb manualKnobs = row();
            knob(manualKnobs, "Strength", 0, 1, 1, () -> State.manualAmounts.strength, v -> State.manualAmounts.strength = v);
            knob(manualKnobs, "Timing", 0, 1, 1, () -> State.manualAmounts.timing, v -> State.manualAmounts.timing = v);
            knob(manualKnobs, "Velocity", -1, 1, 1, () -> State.manualAmounts.velocity, v -> State.manualAmounts.velocity = v);
            dWb manualBox = dWb.MRY(14, new dve[]{graph, manualKnobs});

            dWb all = dWb.MRY(14, new dve[]{tabs, bitwigPanel, fileBox, manualBox});
            Graph.shownWhen.put(bitwigPanel, () -> tab == QUANTIZE);
            Graph.shownWhen.put(fileBox, () -> tab == FILE);
            Graph.shownWhen.put(manualBox, () -> tab == MANUAL);
            try { bitwigPanel.t_(tab == QUANTIZE); fileBox.t_(tab == FILE); manualBox.t_(tab == MANUAL); } catch (Throwable ignored) {}
            return all;
        } catch (Throwable t) {
            State.MOD.log("quantize tabs failed: Bitwig's window as it is", t);
            return bitwigPanel;
        }
    }

    private static dWb row() { dWb r = dWb.IwV(0); try { r.UNj(0.5f); } catch (Throwable ignored) {} return r; }

    private static void knob(dWb row, String name, double min, double max, double dflt,
                             java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        Native.Knob k = new Native.Knob(min, max, dflt, get, v -> { State.willChange("knob " + name); set.accept(v); State.amountsChanged(); }, null);
        k.name = name;
        Graph.panelKnobs.add(k);
        row.HL(k);
        row.Hnt(6);
        row.HL(dut.TMF(name));
        row.Hnt(18);
    }

    /** OK on a groove tab: the groove into the selected notes. */
    static void commit(Object selection) {
        if (tab == FILE) {
            State.Entry e = State.selected();
            GrooveActions.applyTo(selection, e == null ? null : e.groove, State.fileAmounts, "Quantize to Groove");
        } else if (tab == MANUAL) {
            GrooveActions.applyTo(selection, State.manualGroove(), State.manualAmounts, "Quantize to Manual Groove");
        }
    }
}
