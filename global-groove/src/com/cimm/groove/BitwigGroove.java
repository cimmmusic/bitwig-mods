package com.cimm.groove;

import com.bitwig.flt.document.core.master.g2L; // a project

/**
 * Bitwig's own Shuffle and Accent amounts, set to 0 in the project itself while a groove of ours (file or manual) is
 * the source, and put back when the source is Bitwig again. Muting them towards the engine wasn't enough: the engine
 * still added Bitwig's Shuffle (50 % by default) to MIDI recorded from a grooved track, and moving the knob to 0 was
 * the only thing that helped. So the knobs really go to 0; the user's values are kept in the groove settings stored in
 * the project, and the change is part of the same "Change Groove" undo step.
 */
final class BitwigGroove {
    private BitwigGroove() {}

    /** The project's Shuffle and Accent amount values (the ones the engine plays), or null. */
    private static com.bitwig.ramona.core.iYN[] amounts(g2L p) {
        try {
            var groove = ((com.bitwig.flt.document.core.iface.pHd) p).ifN().ugU(); // transport > groove
            return new com.bitwig.ramona.core.iYN[]{((bitwig.obf.Opy) groove.TMF()).Grk(), ((bitwig.obf.Opy) groove.E3()).Grk()}; // Shuffle (j12), Accent (I2d)
        } catch (Throwable t) { State.MOD.log("Bitwig's Shuffle/Accent not found", t); return null; }
    }

    /** Whether the project's Shuffle/Accent don't match the source yet (0 with ours, the user's own with Bitwig's). */
    static boolean needsChange(g2L p) {
        com.bitwig.ramona.core.iYN[] a = amounts(p);
        if (a == null) return false;
        if (State.usesGroove()) return a[0].getDoubleValue() != 0 || a[1].getDoubleValue() != 0;
        return !Double.isNaN(State.savedShuffle) || !Double.isNaN(State.savedAccent);
    }

    /**
     * Brings Shuffle/Accent in line with the source. Call inside an undo step, on the UI thread, BEFORE the settings
     * are written (they carry the saved values). Returns whether anything changed.
     */
    static boolean apply(g2L p) {
        com.bitwig.ramona.core.iYN[] a = amounts(p);
        if (a == null) return false;
        boolean changed = false;
        try {
            if (State.usesGroove()) {
                double sh = a[0].getDoubleValue(), ac = a[1].getDoubleValue();
                if (sh != 0) { if (Double.isNaN(State.savedShuffle)) State.savedShuffle = sh; a[0].setDoubleValue(0); changed = true; }
                if (ac != 0) { if (Double.isNaN(State.savedAccent)) State.savedAccent = ac; a[1].setDoubleValue(0); changed = true; }
                if (changed) State.MOD.log("Bitwig's Shuffle/Accent set to 0 (kept " + State.savedShuffle + " / " + State.savedAccent + ")");
            } else {
                if (!Double.isNaN(State.savedShuffle)) { a[0].setDoubleValue(State.savedShuffle); State.savedShuffle = Double.NaN; changed = true; }
                if (!Double.isNaN(State.savedAccent)) { a[1].setDoubleValue(State.savedAccent); State.savedAccent = Double.NaN; changed = true; }
                if (changed) State.MOD.log("Bitwig's Shuffle/Accent put back");
            }
        } catch (Throwable t) { State.MOD.log("setting Bitwig's Shuffle/Accent failed", t); }
        return changed;
    }
}
