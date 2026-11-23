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
            ((bitwig.obf.dve) list).MRY(new RowsHeight(fxChain, list)); // as tall as its rows (the mixer's list stretches)
            group.HL((bitwig.obf.dve) list);
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: building the insert list failed", t); }
    }

    /** A row's natural height (Bitwig's device row, measured once). */
    private static long rowH = -1;

    static long rowHeight(Object anyDevice) {
        if (rowH > 0) return rowH;
        try {
            Object row = com.bitwig.flt.widget.core.device.g2L.cfM((bitwig.obf.jsd) anyDevice, false, false);
            long h = ((bitwig.obf.dve) row).iG_().Hnt;
            if (h > 4 && h < 200) { rowH = h; ClipFx.MOD.log("Clip FX: device row height " + h); }
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: measuring a device row failed", t); }
        return rowH > 0 ? rowH : 24;
    }

    private static final long PLAIN = 24;
    private static int logs;

    /** Height = the list's own rows as Bitwig built them (each row's natural height, the hidden gate left out, the
     *  + row included), so nothing is squeezed; no stretching. */
    private static final class RowsHeight implements bitwig.obf.svm {
        private final Object chain, list;
        RowsHeight(Object chain, Object list) { this.chain = chain; this.list = list; }
        private long h() {
            try {
                java.util.List<?> kids = ((bitwig.obf.dvW) list).jl_();
                // the rows may sit one level down, in a single holder
                if (kids.size() == 1 && kids.get(0) instanceof bitwig.obf.dvW only && !MixerHide.hidden.contains(only)) kids = only.jl_();
                long sum = 0;
                StringBuilder b = logs < 3 ? new StringBuilder() : null;
                for (Object k : kids) {
                    if (MixerHide.hidden.contains(k)) continue;
                    long n = ((bitwig.obf.dve) k).iG_().Hnt;
                    sum += Math.max(0, n);
                    if (b != null) b.append(k.getClass().getSimpleName()).append('=').append(n).append(' ');
                }
                if (b != null) { logs++; ClipFx.MOD.log("Clip FX: insert list rows " + b + "-> " + sum); }
                if (sum > 4) return sum + 2;
            } catch (Throwable t) { if (logs++ < 3) ClipFx.MOD.log("Clip FX: measuring the insert list failed", t); }
            // not laid out yet: one plain row per insert and the +
            try { return (((com.bitwig.flt.document.core.master.device.ISf) chain).HL().size()) * PLAIN + 2; } catch (Throwable t) { return PLAIN + 2; }
        }
        @Override public long Hnt(bitwig.obf.ruA r) { return h(); }
        @Override public long MRY(bitwig.obf.ruA r) { return h(); }
        @Override public long cfM(bitwig.obf.ruA r) { return h(); }
    }
}
