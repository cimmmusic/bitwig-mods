package com.cimm.groove;

import bitwig.obf.dWb; // a box layout
import bitwig.obf.dut; // a text label
import bitwig.obf.dve; // a widget
import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * The Manual groove graph as a panel of its own (Panels framework): a Groove button in the bottom bar opens it under the
 * arranger (or in the view stack), where it can be made as big as wanted. The graph fills the panel; under it, Bitwig's
 * own buttons: Use Manual, Undo, Redo, Reset, Copy from Groove File, Save as Groove…, and the Base.
 * It edits the same groove as the Play menu, and both stay in step.
 */
public final class GroovePanels implements PanelProvider {
    @Override public void declare(Panels panels) {
        panels.view("groove", "Groove")
              .icon("groove_icon")
              .content(context -> { try { Hooks.activateWindow(context.window()); } catch (Throwable ignored) {} return build(); })
              .scale(true);
    }

    /** Bitwig's knob and its name, for the Manual groove's amounts. */
    private static void panelKnob(dWb row, String name, double min, double max, double dflt,
                                  java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        Native.Knob k = new Native.Knob(min, max, dflt, get, v -> { State.willChange("knob " + name); set.accept(v); State.amountsChanged(); }, null);
        k.name = name;
        Graph.panelKnobs.add(k);
        row.HL(k);
        row.Hnt(8);
        row.HL(dut.TMF(name));
    }

    static dve build() {
        try {
            State.load();
            Graph graph = new Graph(true);
            Graph.panels.add(graph);

            dWb tools = dWb.IwV(0);
            try { tools.UNj(0.5f); } catch (Throwable ignored) {}
            tools.HL(dut.TMF("Edit"));
            tools.Hnt(8);
            for (int i = 0; i < State.SLOT_NAMES.length; i++) {
                int at = i;
                if (i > 0) tools.Hnt(4);
                Native.Bool on = new Native.Bool(() -> State.slot == at, () -> State.setSlot(at));
                Graph.panelValues.add(on);
                tools.HL(Native.option(State.SLOT_NAMES[i], 34, on));
            }
            tools.Hnt(16);
            Native.Bool manualOn = new Native.Bool(() -> State.mode == State.MANUAL, () -> State.setMode(State.MANUAL));
            Graph.panelValues.add(manualOn);
            tools.HL(Native.option("Use Manual", 120, manualOn));
            tools.Hnt(16);
            tools.HL(Native.action("Undo", 80, State::manualUndo));
            tools.Hnt(8);
            tools.HL(Native.action("Redo", 80, State::manualRedo));
            tools.Hnt(16);
            tools.HL(Native.action("Reset", 80, State::manualReset));
            tools.Hnt(8);
            tools.HL(Native.action("Copy from Groove File", 200, State::manualFromFile));
            tools.Hnt(8);
            tools.HL(Native.action("Save as Groove…", 150, Ui::saveAs));
            tools.HL(new Native.Stretch(30));
            panelKnob(tools, "Strength", 0, 1, 1, () -> State.manualAmounts.strength, v -> State.manualAmounts.strength = v);
            tools.Hnt(20);
            panelKnob(tools, "Timing", 0, 1, 1, () -> State.manualAmounts.timing, v -> State.manualAmounts.timing = v);
            tools.Hnt(20);
            panelKnob(tools, "Velocity", -1, 1, 1, () -> State.manualAmounts.velocity, v -> State.manualAmounts.velocity = v);
            tools.Hnt(24);
            tools.HL(dut.TMF("Base"));
            tools.Hnt(12);
            for (int i = 0; i < Groove.BASE_NAMES.length; i++) {
                int at = i;
                if (i > 0) tools.Hnt(6);
                Native.Bool on = new Native.Bool(() -> State.manualAmounts.base == at,
                        () -> { State.willChange(null); State.manualAmounts.base = at; State.amountsChanged(); });
                Graph.panelValues.add(on);
                tools.HL(Native.option(Groove.BASE_NAMES[i], 56, on));
            }
            return dWb.MRY(10, new dve[]{graph, tools});
        } catch (Throwable t) {
            State.MOD.log("groove panel failed", t);
            return dut.TMF("The Groove panel couldn't be built: see the log.");
        }
    }
}
