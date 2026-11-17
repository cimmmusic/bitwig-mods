package com.cimm.mixerpregain;

import bitwig.obf.hSh; // a settable parameter
import bml.api.Bml;

/** Logging and undoable parameter changes. */
final class Log {
    static final Bml.Mod MOD = Bml.mod("mixer-pre-gain");
    private static boolean failed;
    private Log() {}

    static void once(String what, Throwable t) { if (!failed) { failed = true; MOD.log(what, t); } }

    /** Set a parameter (clamped) as an undoable change; changes with the same key within 1.5 s merge into one step. */
    static void set(Params.P p, double value, Object undoKey, String what) {
        if (p == null) return;
        double v = Math.max(p.min(), Math.min(p.max(), value));
        hSh h = p.param();
        h.cfM(v, what, new com.bitwig.ramona.core.FDA(undoKey, 1500));
    }
}
