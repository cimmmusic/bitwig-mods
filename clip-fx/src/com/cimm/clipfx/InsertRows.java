package com.cimm.clipfx;

import java.util.ArrayList;
import java.util.List;

/**
 * The clip's inserts shown as Bitwig's own mixer device rows (power icon and name, as in the mixer's Devices
 * section), in a group that is refilled whenever the inserts change.
 */
final class InsertRows {
    final bitwig.obf.dWb group = bitwig.obf.dWb.UNj(1); // a vertical group
    private final List<Object> shown = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();

    /** Rebuilds the rows when the clip's inserts differ from what's shown. UI thread. */
    void update(List<Object> inserts) {
        if (sameAs(inserts)) return;
        try {
            for (Object r : rows) try { ((bitwig.obf.dvW) (Object) group).MJ((bitwig.obf.dve) r); } catch (Throwable ignored) {}
            rows.clear(); shown.clear();
            for (Object d : inserts) {
                Object row = com.bitwig.flt.widget.core.device.g2L.cfM((bitwig.obf.jsd) d, false, false); // Bitwig's device row
                group.HL((bitwig.obf.dve) row);
                rows.add(row); shown.add(d);
            }
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: building the insert rows failed", t); }
    }

    private boolean sameAs(List<Object> inserts) {
        if (inserts.size() != shown.size()) return false;
        for (int i = 0; i < inserts.size(); i++) if (inserts.get(i) != shown.get(i)) return false;
        return true;
    }
}
