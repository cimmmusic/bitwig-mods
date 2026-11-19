package com.cimm.mixerminipanels;

import bitwig.obf.hSh; // a settable (knob-like) parameter
import bitwig.obf.jsd; // a device
import bitwig.obf.v3U; // any device parameter
import bml.api.Bml;

/**
 * "Reset Device to Default" (mini view right-click): every parameter of the device back to its default.
 *
 * Defaults, Bitwig 6.1.4:
 * - knob-like (hSh): the range QNk() holds the default in plain units, Grk() (its normalised form is gDs.aS_()).
 * - choices (Lig): v3U.aS_() is the default as 0..1; the choice definition DNo() (aX) turns that into the
 *   stored option value with cfM(double).
 * - switches (Qe): v3U.aS_() ≥ 0.5 = on.
 * Undo: the whole reset is ONE named undo step, opened and closed as Bitwig's own actions do it (AVL's protected
 * helpers, done here through their public parts): the document's undo manager ULc() (Rzi), if recording (eLY()),
 * its history vpw() (XKG) opens a step with cfM(name, "", set) and closes it with cfM(false). Inside it the values
 * are set with the plain setters (knobs d_), so every change lands in that one step. Before this, switches and
 * choices were set outside any step and so never reached the undo history.
 */
final class Reset {
    private static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    private static boolean logged;

    private Reset() {}

    static void device(jsd device) {
        String[] ids = null;
        try { ids = device.qm(); } catch (Throwable t) { MOD.log("reset: no parameters", t); }
        if (ids == null) return;
        com.bitwig.ramona.core.XKG step = open(device, ids);
        Object undo = new Object();
        int knobs = 0, choices = 0, switches = 0, failed = 0;
        StringBuilder log = logged ? null : new StringBuilder("reset '" + Params.safeName(device) + "':\n");
        for (String id : ids) {
            try {
                v3U p = device.MRY(id);
                if (p instanceof hSh h) {
                    double def = h.QNk().Grk(), was = h.iCS();
                    if (Math.abs(def - was) > 1e-12) {
                        if (step != null) h.d_(def); // recorded in our open step
                        else h.cfM(def, "Reset Device to Default", new com.bitwig.ramona.core.FDA(undo, 1500));
                        knobs++;
                    }
                    if (log != null) log.append("  ").append(id).append(": ").append(was).append(" -> ").append(def).append("\n");
                } else if (p instanceof bitwig.obf.Lig l) {
                    int def = l.DNo().cfM(p.aS_());
                    EqGraph.setChoice(l, def);
                    choices++;
                    if (log != null) log.append("  ").append(id).append(" (choice) -> ").append(def).append("\n");
                } else if (p instanceof bitwig.obf.Qe q) {
                    boolean def = p.aS_() >= 0.5;
                    q.eLY(def); // which getter holds the current state isn't known, so always set
                    switches++;
                    if (log != null) log.append("  ").append(id).append(" (switch) -> ").append(def).append("\n");
                }
            } catch (Throwable t) {
                failed++;
                if (failed <= 3) MOD.log("reset: " + id + " failed", t);
            }
        }
        if (step != null) {
            try { step.cfM(false); } catch (Throwable t) { MOD.log("reset: closing the undo step failed", t); }
        }
        if (log != null) { logged = true; MOD.log(log.toString()); }
        MOD.log("reset '" + Params.safeName(device) + "': " + knobs + " knobs changed, " + choices + " choices and "
                + switches + " switches set" + (failed > 0 ? ", " + failed + " failed" : "")
                + (step != null ? " (one undo step)" : " (no undo step could be opened)"));
    }

    /** Opens the "Reset Device to Default" undo step in the device's document; null if it can't. */
    private static com.bitwig.ramona.core.XKG open(jsd device, String[] ids) {
        try {
            for (String id : ids) {
                v3U p = device.MRY(id);
                if (!(p instanceof com.bitwig.ramona.core.AVL a)) continue;
                com.bitwig.ramona.core.Rzi undo = a.ULc();
                if (undo == null || !undo.eLY()) return null;
                com.bitwig.ramona.core.XKG history = undo.vpw();
                return history.cfM("Reset Device to Default", "", new java.util.HashSet<>()) ? history : null;
            }
        } catch (Throwable t) { MOD.log("reset: can't open an undo step", t); }
        return null;
    }
}
