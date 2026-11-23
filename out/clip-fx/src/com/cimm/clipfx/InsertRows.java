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
            group.HL((bitwig.obf.dve) list);
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: building the insert list failed", t); }
    }
}
