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
    private final List<Object> rows = new ArrayList<>(); // every widget in the group (lines and rows)
    final List<DropLine> lines = new ArrayList<>();      // lines[i]: above insert i; lines[n]: below the last

    /** While dragging insert 'from' by 'by' places: the line where it lands is lit (none for no move). */
    void preview(int from, int by) {
        int to = Math.max(0, Math.min(lines.size() - 2, from + by));
        int slot = to == from ? -1 : to > from ? to + 1 : to;
        for (int i = 0; i < lines.size(); i++) lines.get(i).light(i == slot);
    }

    /** Rebuilds the rows when the clip's inserts differ from what's shown. UI thread. */
    void update(List<Object> inserts) {
        if (sameAs(inserts)) return;
        try {
            for (Object r : rows) try { ((bitwig.obf.dvW) (Object) group).MJ((bitwig.obf.dve) r); } catch (Throwable ignored) {}
            rows.clear(); shown.clear(); lines.clear();
            int index = 0;
            for (Object d : inserts) {
                DropLine line = new DropLine();
                group.HL(line); rows.add(line); lines.add(line);
                Object device = com.bitwig.flt.widget.core.device.g2L.cfM((bitwig.obf.jsd) d, false, false); // Bitwig's device row
                // the row and, at its right, a grip to drag it up or down
                Object row = bitwig.obf.dWb.cfM(com.bitwig.base.geom.LWw.cfM, 0, new bitwig.obf.dve[]{(bitwig.obf.dve) device, new Grip(d, this, index++)});
                group.HL((bitwig.obf.dve) row);
                rows.add(row); shown.add(d);
            }
            if (!inserts.isEmpty()) { DropLine last = new DropLine(); group.HL(last); rows.add(last); lines.add(last); }
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: building the insert rows failed", t); }
    }

    private boolean sameAs(List<Object> inserts) {
        if (inserts.size() != shown.size()) return false;
        for (int i = 0; i < inserts.size(); i++) if (inserts.get(i) != shown.get(i)) return false;
        return true;
    }
}
