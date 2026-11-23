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

    /** Each insert's row height, measured as the list builds it (with a Mini Panels graph, if any). */
    private static final java.util.Map<Object, Long> heights = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Height = each insert's row (the gate is hidden) + one row for the list's +; no stretching. */
    private static final class RowsHeight implements bitwig.obf.svm {
        private final Object chain, list;
        RowsHeight(Object chain, Object list) { this.chain = chain; this.list = list; }
        private long h() {
            try {
                java.util.List<?> d = ((com.bitwig.flt.document.core.master.device.ISf) chain).HL();
                // the + row: a plain row (the smallest insert row seen, else Bitwig's usual 24)
                long plain = PLAIN, sum = 0;
                for (int i = 1; i < d.size(); i++) { long h = heightOf(d.get(i), PLAIN); sum += h; if (h < plain && h > 8) plain = h; }
                return sum + plain + 2;
            } catch (Throwable t) { return 26; }
        }
        private long heightOf(Object device, long row) {
            Long known = heights.get(device);
            if (known != null) return known;
            long h = row;
            try {
                Object w = ((com.bitwig.flt.widget.core.device.uMi) list).MRY((bitwig.obf.jsd) device);
                long n = ((bitwig.obf.dve) w).iG_().Hnt;
                if (n > 4 && n < 1000) h = n;
            } catch (Throwable ignored) {}
            heights.put(device, h);
            return h;
        }
        @Override public long Hnt(bitwig.obf.ruA r) { return h(); }
        @Override public long MRY(bitwig.obf.ruA r) { return h(); }
        @Override public long cfM(bitwig.obf.ruA r) { return h(); }
    }
}
