package com.cimm.clipfx;

import com.bitwig.flt.document.core.master.device.AxS;
import com.bitwig.flt.document.core.master.device.ISf;
import com.bitwig.flt.document.core.master.device.xlG;
import com.bitwig.flt.document.core.master.g2L;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns Clip FX on for an arranger clip, as one undo step: a Clip FX Unit for the clip at the end of its track (an FX
 * Layer: Dry layer with a Tool, FX layer with a Tool at -inf followed by the clip's inserts), and automation inside the
 * clip that opens the FX layer and closes the Dry one for exactly the clip's span, written with Bitwig's own
 * insert_arranger_clip_automation_events on the track's automation lanes.
 */
final class ClipGate {
    private ClipGate() {}

    /** UI thread. Returns the unit, or null (logged). */
    static AxS turnOn(Object clip) {
        bitwig.obf.xJo track = null;
        g2L project = null;
        for (Object x = clip; x != null; x = Probe.parent(x)) {
            if (track == null && x instanceof bitwig.obf.xJo t) track = t;
            if (x instanceof g2L p) project = p;
        }
        if (track == null || project == null) { ClipFx.MOD.log("Clip FX: no track / project above the clip"); return null; }
        if (!(clip instanceof bitwig.obf.Ob c)) { ClipFx.MOD.log("Clip FX: only arranger clips so far (" + clip.getClass().getSimpleName() + ")"); return null; }
        try {
            // as Bitwig's DAWproject export reads a clip: time = x3t(), duration = nfq()
            bitwig.obf.zZi z = (bitwig.obf.zZi) (Object) clip;
            double start = z.x3t(), end = start + z.nfq();
            ClipFx.MOD.log("Clip FX: clip " + start + " .. " + end);

            Object chainObj = ChainBuilder.trackChain(track);
            if (!(chainObj instanceof ISf chain)) { ClipFx.MOD.log("Clip FX: the track's chain wasn't found"); return null; }
            java.io.File preset = ChainBuilder.preset("Clip FX Unit");
            if (preset == null) { ClipFx.MOD.log("Clip FX: Clip FX Unit preset not found"); return null; }
            Object lanes = track.iSC(); // the track's automation lanes (attached; meo() is a detached copy)
            Object selection = project.EV().iAj(); // the arranger's selection, as Bitwig passes it
            Object found = ((bitwig.obf.der) (Object) bitwig.obf.BTS.cfM).cfM("insert_arranger_clip_automation_events");
            if (!(found instanceof bitwig.obf.deV op) || !Probe.path(lanes).endsWith("automation_lanes") || selection == null) {
                ClipFx.MOD.log("Clip FX: can't write automation (operation " + (found != null) + ", lanes '" + Probe.path(lanes) + "', selection " + (selection != null) + ")");
                return null;
            }
            xlG u = ChainBuilder.load(preset);
            project.cfM("Clip FX On", false); // one undo step for the unit and its gate
            try {
                chain.cfM(u, chain.HL().size());
                AxS unit = (AxS) u;
                bitwig.obf.rce[] vol = GateTest.volumes(unit);
                if (vol[0] == null || vol[1] == null) { ClipFx.MOD.log("Clip FX: the unit's gates weren't found"); return unit; }
                bitwig.obf.jRs hold = bitwig.obf.jRs.values()[0]; // HOLD
                write(op, lanes, vol[1], start, end, 1, hold, selection); // FX layer open
                write(op, lanes, vol[0], start, end, 0, hold, selection); // Dry layer closed
                ClipFx.MOD.log("Clip FX: on, unit " + Probe.path(unit));
                return unit;
            } finally { project.cfM(false); }
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: turning on failed", t); return null; }
    }

    private static void write(bitwig.obf.deV op, Object lanes, bitwig.obf.rce param, double start, double end, double value,
                              bitwig.obf.jRs mode, Object selection) {
        List<Object> args = new ArrayList<>(Arrays.asList(
                ((bitwig.obf.bDH) (Object) param).Mph(),
                new double[]{start, end}, new double[]{value, value}, mode, new double[]{0, 0},
                Boolean.FALSE, null, 0, selection));
        op.cfM((com.bitwig.ramona.core.oDc) lanes, args);
    }
}
