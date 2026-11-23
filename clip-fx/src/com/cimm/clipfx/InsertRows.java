package com.cimm.clipfx;

/**
 * The clip's inserts as Bitwig's own mixer Devices list (uMi) for the clip's FX layer: the same rows, clicking,
 * double-click, dragging to reorder with the white drop line, as in the mixer. The gate (first device) is hidden
 * from it (MixerHide). Rebuilt only when the selected clip's FX layer changes.
 */
final class InsertRows {
    final bitwig.obf.dWb group = bitwig.obf.dWb.UNj(1); // a vertical group holding the list
    private Object chain, list;

    /** Shows the list for this FX layer (null: none). UI thread. */
    void show(Object fxChain) {
        if (fxChain == chain) return;
        try {
            if (list != null) try { ((bitwig.obf.dvW) (Object) group).MJ((bitwig.obf.dve) list); } catch (Throwable ignored) {}
            list = null; chain = fxChain;
            if (fxChain == null) return;
            list = com.bitwig.flt.widget.core.device.uMi.cfM((bitwig.obf.FAu) fxChain, true, true);
            noStretch((bitwig.obf.dve) list);
            group.HL((bitwig.obf.dve) list);
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: building the insert list failed", t); }
    }

    /**
     * The mixer's list stretches to fill its section; here it keeps its natural height. Its height policy (min,
     * preferred, max: Hnt, MRY, cfM) is wrapped so the max is the preferred. Which getter holds the height policy is
     * found by setting a marker with the height setter (MRY) and seeing which getter returns it.
     */
    private static void noStretch(bitwig.obf.dve w) {
        try {
            java.util.List<java.util.function.Supplier<bitwig.obf.svm>> getters = java.util.List.of(w::jG_, w::iR_, w::jH_, w::jx_);
            bitwig.obf.svm[] before = new bitwig.obf.svm[getters.size()];
            for (int i = 0; i < before.length; i++) before[i] = getters.get(i).get();
            bitwig.obf.svm marker = new Policy(null, w);
            w.MRY(marker);
            int at = -1;
            for (int i = 0; i < before.length; i++) if (getters.get(i).get() == marker) at = i;
            bitwig.obf.svm orig = at >= 0 ? before[at] : null;
            w.MRY(new Policy(orig, w));
            if (logs++ < 1) ClipFx.MOD.log("Clip FX: insert list height policy getter #" + at + " (" + (orig == null ? "none" : orig.getClass().getSimpleName()) + ")");
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: keeping the insert list small failed", t); }
    }
    private static int logs;

    /** Min and preferred as the original; max = preferred (no stretching). Without an original: the list's own
     *  natural height (iG_()) for all three. */
    private static final class Policy implements bitwig.obf.svm {
        private final bitwig.obf.svm o;
        private final bitwig.obf.dve w;
        Policy(bitwig.obf.svm o, bitwig.obf.dve w) { this.o = o; this.w = w; }
        private long natural() { try { return w.iG_().Hnt; } catch (Throwable t) { return 24; } }
        @Override public long Hnt(bitwig.obf.ruA r) { return o == null ? natural() : o.Hnt(r); }
        @Override public long MRY(bitwig.obf.ruA r) { return o == null ? natural() : o.MRY(r); }
        @Override public long cfM(bitwig.obf.ruA r) { return o == null ? natural() : Math.max(o.Hnt(r), o.MRY(r)); }
    }
}
