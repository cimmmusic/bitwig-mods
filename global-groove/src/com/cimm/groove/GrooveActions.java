package com.cimm.groove;

import bml.actions.ActionContext;
import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The notes' right-click menu: a Groove submenu that writes a groove into the selected notes (one Undo step, Bitwig's
 * own), and takes a groove out of them (into the Manual graph, or saved as an .agr).
 *
 * Applying moves each note's start as playback would and keeps its length exactly; velocities as the Velocity
 * amount says.
 */
public final class GrooveActions implements ActionProvider {
    private static final Menu[] NOTES = {
            Menu.selection("arranger_clip_instrument_note"), Menu.selection("launcher_clip_instrument_note"),
            Menu.selection("arranger_instrument_note"), Menu.selection("launcher_instrument_note")};

    @Override public void declare(Actions actions) {
        State.load();
        var groove = actions.submenu("groove", "Groove").in(NOTES);
        groove.action("cimm_groove_apply_file", "Apply Groove File to Notes")
              .visibleWhen(c -> State.selected() != null)
              .onRun(c -> apply(c, State.selected() == null ? null : State.selected().groove, State.fileAmounts, "Apply Groove File"));
        groove.action("cimm_groove_apply_manual", "Apply Manual Groove to Notes")
              .onRun(c -> apply(c, State.manualGroove(), State.manualAmounts, "Apply Manual Groove"));
        groove.action("cimm_groove_extract", "Extract Groove to Manual Graph")
              .onRun(c -> extract(c, false));
        groove.action("cimm_groove_extract_save", "Extract Groove and Save as .agr…")
              .onRun(c -> extract(c, true));
    }

    /** The selected notes, each once (a note selected in several places counts once). */
    private static List<Note> notes(ActionContext c) {
        return new ArrayList<>(new LinkedHashSet<>(PianoRoll.selection(c.selection()).notes()));
    }

    static void apply(ActionContext c, Groove g, Groove.Amounts a, String undoLabel) { applyTo(c.selection(), g, a, undoLabel); }

    /** The groove into a note selection (a menu's, or a note editor's from a hook); null: the one note editor's. */
    static void applyTo(Object selection, Groove g, Groove.Amounts a, String undoLabel) {
        try {
            if (g == null) throw new Refused("no groove to apply");
            List<Note> notes;
            try { notes = new ArrayList<>(new LinkedHashSet<>(PianoRoll.selection(selection).notes())); }
            catch (RuntimeException ex) { notes = new ArrayList<>(new LinkedHashSet<>(PianoRoll.selection(null).notes())); }
            if (notes.isEmpty()) throw new Refused("no notes selected");
            Edit edit = PianoRoll.edit(undoLabel);
            int changed = 0;
            for (Note n : notes) {
                double s = n.start();
                double ns = g.time(s, a, seed(s));
                if (s >= 0 && ns < 0) ns = 0;
                double v = g.velocity(s, n.velocity(), a);
                if (Math.abs(ns - s) > 1e-9) edit.move(n, ns); // move keeps the note's length
                if (Math.abs(v - n.velocity()) > 1e-9) edit.setVelocity(n, Math.max(0, Math.min(1, v)));
                changed++;
            }
            edit.apply();
            State.MOD.log(undoLabel + ": " + changed + " note(s)");
        } catch (Refused r) {
            State.MOD.log(undoLabel + ": nothing changed: " + r.getMessage());
        } catch (Throwable t) {
            State.MOD.log(undoLabel + " failed", t);
        }
    }

    /** The same seed for the same position as playback uses, so Random matches what was heard. */
    private static long seed(double t) { return Math.round(t * 960) * 0x9E3779B97F4A7C15L; }

    /**
     * The selected notes' timing and velocity as the Manual graph (at its Base): each step takes the average offset and
     * velocity of the notes nearest to it, across every bar of the selection. Steps with no note stay straight.
     */
    static void extract(ActionContext c, boolean save) {
        try {
            List<Note> notes = notes(c);
            if (notes.isEmpty()) throw new Refused("no notes selected");
            int base = State.manualAmounts.base;
            double b = Groove.BASES[base];
            int steps = State.manualSteps(base);
            double[] off = new double[steps], vel = new double[steps];
            int[] count = new int[steps];
            double origin = Double.MAX_VALUE;
            for (Note n : notes) origin = Math.min(origin, n.start());
            origin = Math.floor(origin / State.BAR) * State.BAR; // from the bar the selection starts in
            for (Note n : notes) {
                double pos = n.start() - origin;
                long s = Math.round(pos / b);
                double d = pos - s * b;
                if (Math.abs(d) > b / 2) continue;
                int k = (int) Math.floorMod(s, (long) steps);
                off[k] += d / b; vel[k] += n.velocity() * 127; count[k]++;
            }
            State.manualWillChange();
            double[][] m = State.manual(base);
            for (int k = 0; k < steps; k++) {
                m[0][k] = count[k] == 0 ? 0 : Math.max(-0.5, Math.min(0.5, off[k] / count[k]));
                m[1][k] = count[k] == 0 ? 100 : Math.max(1, Math.min(127, Math.round(vel[k] / count[k])));
            }
            State.manualChanged();
            State.MOD.log("groove extracted from " + notes.size() + " note(s) into the Manual graph");
            if (save) Ui.saveAs();
        } catch (Refused r) {
            State.MOD.log("extract groove: nothing changed: " + r.getMessage());
        } catch (Throwable t) {
            State.MOD.log("extract groove failed", t);
        }
    }
}
