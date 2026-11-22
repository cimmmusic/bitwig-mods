package com.cimm.groove;

import bitwig.obf.dKB; // a menu-items provider (addMenuItems(menu))
import bitwig.obf.dKW; // a popup menu being filled
import bitwig.obf.dLh; // a button that opens a menu
import bitwig.obf.dWb; // a box layout: MRY(gap, kids) vertical, cfM(gap, kids) horizontal
import bitwig.obf.dut; // a text label
import bitwig.obf.dve; // a widget
import bitwig.obf.dyW; // styles a menu button (we leave it as is)
import com.bitwig.flt.app.CHR; // the application: asyncExec runs on the UI thread

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the GROOVE section's additions: a Source switch under Enable Groove, and in Groove File mode the groove picker
 * and its amounts in place of Bitwig's Shuffle and Accent rows (which stay as they are in Bitwig mode).
 */
final class Ui {
    private Ui() {}

    /** Every native value shown in the current menu, told to update after a change. */
    private static final List<Native.Value> values = new ArrayList<>();
    private static final List<dve> knobs = new ArrayList<>();
    private static final List<Runnable> refreshers = new ArrayList<>();
    private static String dragging; private static long draggedAt;

    /** A horizontal row, children centred vertically. */
    private static dWb row() { dWb r = dWb.IwV(0); try { r.UNj(0.5f); } catch (Throwable ignored) {} return r; }

    private static <V extends Native.Value> V value(V v) { values.add(v); return v; }

    /** Wraps Bitwig's Shuffle/Accent rows (bitwigRows) into the section's new contents, in Bitwig's own widgets. */
    static dWb build(int gap, dWb bitwigRows) {
        values.clear(); knobs.clear(); refreshers.clear();
        State.clearViews(); // one Play menu at a time: drop the last one's widgets

        // Source  [Bitwig][Groove File][Manual]
        dWb source = row();
        source.HL(dut.TMF("Source"));
        source.Hnt(40);
        String[] names = {"Bitwig", "Groove File", "Manual"};
        long[] widths = {110, 140, 110};
        for (int i = 0; i < 3; i++) {
            int m = i;
            source.HL(Native.option(names[i], widths[i], value(new Native.Bool(() -> State.mode == m, () -> State.setMode(m)))));
        }
        // Manual [A][B][C][D]: which drawing the global Manual source plays (editing another one doesn't change it)
        source.Hnt(10);
        for (int i = 0; i < State.SLOT_NAMES.length; i++) {
            int at = i;
            if (i > 0) source.Hnt(3);
            source.HL(Native.option(State.SLOT_NAMES[i], 30, value(new Native.Bool(() -> State.playSlot == at,
                    () -> { State.setPlaySlot(at); if (State.mode != State.MANUAL) State.setMode(State.MANUAL); }))));
        }

        // [groove name           ▾]  [◀] [▶] [Browse]
        dWb grooveRow = row();
        grooveRow.HL(chooser());
        grooveRow.Hnt(16);
        grooveRow.HL(Native.action("◀", 38, () -> State.step(-1)));
        grooveRow.Hnt(8);
        grooveRow.HL(Native.action("▶", 38, () -> State.step(1)));
        grooveRow.Hnt(8);
        grooveRow.HL(Native.action("Browse", 118, Ui::browse));

        // knobs: 4 + 1, names to the right (the value shows in the name while dragging)
        dWb knobs1 = row(), knobs2 = row();
        knob(knobs1, "Strength", 0, 1, 1, () -> State.amounts().strength, v -> State.amounts().strength = v);
        knobs1.Hnt(34);
        knob(knobs1, "Timing", 0, 1, 1, () -> State.amounts().timing, v -> State.amounts().timing = v);
        knobs1.Hnt(34);
        knob(knobs1, "Velocity", -1, 1, 0, () -> State.amounts().velocity, v -> State.amounts().velocity = v);
        knobs1.Hnt(34);
        knob(knobs1, "Random", 0, 1, 0, () -> State.amounts().random, v -> State.amounts().random = v);
        knob(knobs2, "Quantize", 0, 1, 0, () -> State.amounts().quantize, v -> State.amounts().quantize = v);

        // Base  [1/4] [1/8] [1/8T] [1/16] [1/16T] [1/32]
        dWb baseRow = row();
        baseRow.HL(dut.TMF("Base"));
        baseRow.Hnt(18);
        for (int i = 0; i < Groove.BASE_NAMES.length; i++) {
            int at = i;
            if (i > 0) baseRow.Hnt(14);
            baseRow.HL(Native.option(Groove.BASE_NAMES[i], 66, value(new Native.Bool(() -> State.amounts().base == at,
                    () -> { State.willChange(null); State.amounts().base = at; State.amountsChanged(); }))));
        }

        // Manual: the graph, then [Reset] [Copy from Groove File]
        Graph graph = new Graph();
        knobs.add(graph);
        dWb manualButtons = row();
        // Slot [A][B][C][D]: which drawing is edited (and played by the global Manual source)
        manualButtons.HL(dut.TMF("Edit"));
        manualButtons.Hnt(10);
        for (int i = 0; i < State.SLOT_NAMES.length; i++) {
            int at = i;
            if (i > 0) manualButtons.Hnt(4);
            manualButtons.HL(Native.option(State.SLOT_NAMES[i], 38, value(new Native.Bool(() -> State.slot == at, () -> State.setSlot(at)))));
        }
        manualButtons.Hnt(16);
        manualButtons.HL(Native.action("Undo", 80, State::manualUndo));
        manualButtons.Hnt(8);
        manualButtons.HL(Native.action("Redo", 80, State::manualRedo));
        manualButtons.Hnt(16);
        manualButtons.HL(Native.action("Reset", 80, State::manualReset));
        manualButtons.Hnt(8);
        manualButtons.HL(Native.action("Copy from Groove File", 200, State::manualFromFile));
        manualButtons.Hnt(8);
        manualButtons.HL(Native.action("Save as Groove…", 150, Ui::saveAs));
        dWb manualBox = dWb.MRY(gap + 10, new dve[]{graph, manualButtons});

        dWb fileBox = dWb.MRY(gap + 10, new dve[]{grooveRow, manualBox, knobs1, knobs2, baseRow});
        dWb all = dWb.MRY(gap + 10, new dve[]{source, bitwigRows, fileBox});

        Runnable show = () -> {
            int mode = State.mode;
            try {
                bitwigRows.t_(mode == State.BITWIG);
                fileBox.t_(mode != State.BITWIG);
                grooveRow.t_(mode == State.FILE);
                manualBox.t_(mode == State.MANUAL);
                for (dve w = all; w != null; w = w.RbC()) w.oQi(); // lay out again up to the menu
            } catch (Throwable t) { State.MOD.log("groove section layout failed", t); }
            for (Native.Value v : values) v.fire();
            for (Runnable r : refreshers) try { r.run(); } catch (Throwable t) { State.MOD.log("refresh failed", t); }
            for (dve k : knobs) try { k.jbi(); } catch (Throwable ignored) {}
        };
        State.onChange(show);
        try {
            bitwigRows.t_(State.mode == State.BITWIG);
            fileBox.t_(State.mode != State.BITWIG);
            grooveRow.t_(State.mode == State.FILE);
            manualBox.t_(State.mode == State.MANUAL);
        } catch (Throwable t) { State.MOD.log("groove section show failed", t); }
        return all;
    }

    /** Bitwig's knob, then its name in a Bitwig label (with the value while it's being dragged). */
    private static void knob(dWb row, String name, double min, double max, double dflt,
                             java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        Native.Text label = value(new Native.Text(() -> name));
        Native.Knob k = new Native.Knob(min, max, dflt, get, v -> { State.willChange("knob " + name); set.accept(v); State.amountsChanged(); }, null);
        k.name = name;
        if (name.equals("Velocity")) k.defaultBy = () -> State.mode == State.MANUAL ? 1 : 0;
        knobs.add(k);
        row.HL(k);
        row.Hnt(10);
        row.HL(dut.cfM(label));
    }

    /** The groove chooser: Bitwig's menu button showing the groove's name, opening the list of loaded grooves. */
    private static dve chooser() {
        Native.Chooser c = new Native.Chooser();
        Runnable update = () -> {
            String msg = State.message;
            State.Entry e = State.selected();
            c.text(msg != null ? msg : e == null ? "No groove loaded: click Browse" : e.groove.name);
        };
        update.run();
        refreshers.add(update);
        try { c.cfM((dKB) menu -> fill(menu)); } catch (Throwable t) { State.MOD.log("groove chooser menu failed", t); }
        return c;
    }

    static void fill(dKW menu) {
        try {
            List<State.Entry> list = State.list();
            State.Entry cur = State.selected();
            for (int i = 0; i < list.size(); i++) {
                int at = i;
                State.Entry e = list.get(i);
                menu.cfM((e == cur ? "✓  " : "     ") + e.groove.name, (Runnable) () -> State.select(at, true));
            }
            if (!list.isEmpty()) menu.ELS();
            menu.cfM("Browse…", (Runnable) Ui::browse);
            if (cur != null) menu.cfM("Remove '" + cur.groove.name + "' from the list", (Runnable) () -> State.remove(State.current));
        } catch (Throwable t) { State.MOD.log("groove menu failed", t); }
    }

    /** The system's own save dialog (in a Custom folder next to the grooves), then writes the Manual groove there. */
    static void saveAs() {
        Thread t = new Thread(() -> {
            try {
                Path folder = grooveFolder().resolve("Custom");
                Files.createDirectories(folder);
                Path file = FileDialogs.save("Save the Manual groove as an Ableton groove (.agr)", folder, "My Groove.agr");
                if (file == null) return; // cancelled
                if (!file.getFileName().toString().toLowerCase().endsWith(".agr")) file = file.resolveSibling(file.getFileName() + ".agr");
                Path chosen = file;
                CHR.Grk().asyncExec(() -> State.saveManual(chosen));
            } catch (Throwable e) { State.MOD.log("save dialog failed", e); }
        }, "cimm-groove-save");
        t.setDaemon(true);
        t.start();
    }

    /** The system's own file chooser (several files at once), off the UI thread; the files load on the UI thread. */
    static void browse() {
        Thread t = new Thread(() -> {
            try {
                String last = State.MOD.settings().get("browse_folder", "");
                Path start = !last.isEmpty() && Files.isDirectory(Path.of(last)) ? Path.of(last) : grooveFolder();
                if (!Files.isDirectory(start)) start = Path.of(System.getProperty("user.home"));
                List<Path> files = FileDialogs.openGrooves("Choose grooves: Ableton .agr or MIDI files", start);
                if (files.isEmpty()) return; // cancelled
                Path parent = files.get(0).getParent();
                if (parent != null) State.MOD.settings().put("browse_folder", parent.toString());
                CHR.Grk().asyncExec(() -> {
                    try {
                        State.addFiles(files);
                        if (State.mode == State.BITWIG && State.selected() != null) State.setMode(State.FILE);
                    } catch (Throwable e) { State.MOD.log("loading grooves failed", e); }
                });
            } catch (Throwable e) { State.MOD.log("file chooser failed", e); }
        }, "cimm-groove-browse");
        t.setDaemon(true);
        t.start();
    }

    /** Desktop/Grooves (on Windows also a OneDrive-backed Desktop), whether or not it exists yet. */
    static Path grooveFolder() {
        Path home = Path.of(System.getProperty("user.home"));
        Path desktop = home.resolve("Desktop");
        if (!Files.isDirectory(desktop) && FileDialogs.WINDOWS) {
            String od = System.getenv("OneDrive");
            if (od != null && Files.isDirectory(Path.of(od, "Desktop"))) desktop = Path.of(od, "Desktop");
        }
        return Files.isDirectory(desktop) ? desktop.resolve("Grooves") : home.resolve("Grooves");
    }

}
