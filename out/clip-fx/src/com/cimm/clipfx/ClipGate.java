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

    /** Everything needed to edit a clip's Clip FX. */
    static final class Ctx {
        bitwig.obf.xJo track; g2L project; ISf chain; Object lanes, selection; bitwig.obf.deV op; double start, end;
    }

    static Ctx context(Object clip) {
        Ctx c = new Ctx();
        for (Object x = clip; x != null; x = Probe.parent(x)) {
            if (c.track == null && x instanceof bitwig.obf.xJo t) c.track = t;
            if (x instanceof g2L p) c.project = p;
        }
        if (c.track == null || c.project == null) { ClipFx.MOD.log("Clip FX: no track / project above the clip"); return null; }
        if (!(clip instanceof bitwig.obf.zZi z)) { ClipFx.MOD.log("Clip FX: only arranger clips so far (" + clip.getClass().getSimpleName() + ")"); return null; }
        // as Bitwig's DAWproject export reads a clip: time = x3t(), duration = nfq()
        c.start = z.x3t(); c.end = c.start + z.nfq();
        if (!(ChainBuilder.trackChain(c.track) instanceof ISf chain)) { ClipFx.MOD.log("Clip FX: the track's chain wasn't found"); return null; }
        c.chain = chain;
        c.lanes = c.track.iSC();                 // the track's automation lanes (attached; meo() is a detached copy)
        c.selection = c.project.EV().iAj();      // the arranger's selection, as Bitwig passes it
        Object found = ((bitwig.obf.der) (Object) bitwig.obf.BTS.cfM).cfM("insert_arranger_clip_automation_events");
        if (!(found instanceof bitwig.obf.deV op) || !Probe.path(c.lanes).endsWith("automation_lanes") || c.selection == null) {
            ClipFx.MOD.log("Clip FX: can't write automation (operation " + (found != null) + ", lanes '" + Probe.path(c.lanes) + "', selection " + (c.selection != null) + ")");
            return null;
        }
        c.op = op;
        return c;
    }

    /** Clip FX on for a clip without a unit: the unit at the end of its track and its gate open, one undo step. */
    static AxS turnOn(Object clip) {
        Ctx c = context(clip);
        if (c == null) return null;
        try {
            java.io.File preset = ChainBuilder.preset("Clip FX Unit");
            if (preset == null) { ClipFx.MOD.log("Clip FX: Clip FX Unit preset not found"); return null; }
            xlG u = ChainBuilder.load(preset);
            c.project.cfM("Clip FX On", false);
            try {
                c.chain.cfM(u, c.chain.HL().size());
                gate(c, (AxS) u, true);
                ClipFx.units.add(u);
                ClipFx.MOD.log("Clip FX: on (new unit " + Probe.path(u) + "), clip " + c.start + " .. " + c.end);
                return (AxS) u;
            } finally { c.project.cfM(false); }
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: turning on failed", t); return null; }
    }

    /** On / off for a clip that has its unit: rewrites its gate over the clip (the unit and its inserts stay). */
    static void set(Object clip, AxS unit, boolean on) {
        Ctx c = context(clip);
        if (c == null) return;
        try {
            c.project.cfM(on ? "Clip FX On" : "Clip FX Off", false);
            try { gate(c, unit, on); } finally { c.project.cfM(false); }
            ClipFx.MOD.log("Clip FX: " + (on ? "on" : "off") + ", clip " + c.start + " .. " + c.end);
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: switching failed", t); }
    }

    /** On: FX layer open, Dry closed over the clip. Off: FX closed, Dry open (the clip plays dry). Inside an undo step. */
    private static void gate(Ctx c, AxS unit, boolean on) {
        bitwig.obf.rce[] vol = GateTest.volumes(unit);
        if (vol[0] == null || vol[1] == null) { ClipFx.MOD.log("Clip FX: the unit's gates weren't found"); return; }
        bitwig.obf.jRs hold = bitwig.obf.jRs.values()[0]; // HOLD
        // automation takes normalized positions (1.0 is the top of the knob, +18 dB): open = where 0 dB sits
        double open = zeroDb(vol[1]), dryOpen = zeroDb(vol[0]);
        write(c.op, c.lanes, vol[1], c.start, c.end, on ? open : 0, hold, c.selection);    // FX layer
        write(c.op, c.lanes, vol[0], c.start, c.end, on ? 0 : dryOpen, hold, c.selection); // Dry layer
    }

    /** Deletes a device the way Bitwig does (the project's delete_devices), as one undo step. UI thread. */
    static void delete(Object device) {
        g2L project = null;
        for (Object x = device; x != null; x = Probe.parent(x)) if (x instanceof g2L p) project = p;
        if (project == null) { ClipFx.MOD.log("Clip FX: no project above the device"); return; }
        Object found = ((bitwig.obf.der) (Object) com.bitwig.flt.document.core.master.Ljp.cfM).cfM("delete_devices");
        if (!(found instanceof bitwig.obf.deV op)) { ClipFx.MOD.log("Clip FX: delete_devices not found"); return; }
        project.cfM("Remove Clip FX Insert", false);
        try {
            op.cfM(project, new ArrayList<>(List.of(new ArrayList<>(List.of(device)))));
            ClipFx.MOD.log("Clip FX: removed " + Probe.path(device));
        } catch (Throwable t) { ClipFx.MOD.log("Clip FX: removing failed", t); }
        finally { project.cfM(false); }
    }

    /**
     * A Tool Volume knob's normalized position for 0 dB. Its automation is cubic up to +18 dB (gain 8 x position^3:
     * 1.0 = +18.1 dB, 0.8 = +12 dB, as seen in Bitwig), so 0 dB (gain 1) is at 0.5.
     */
    static double zeroDb(bitwig.obf.rce volume) {
        if (zeroLogs++ < 1) ClipFx.MOD.log("Clip FX: open gate at 0.5 (0 dB on the Tool's Volume)");
        return 0.5;
    }
    private static int zeroLogs;

    /** The unit's FX layer chain: the Tool (its gate) first, then the clip's inserts. */
    static ISf fxChain(AxS unit) {
        for (Object part : unit.puu().E3()) {
            if (!(part instanceof com.bitwig.flt.document.core.master.device.Dys layers)) continue;
            int i = 0;
            for (Object l : layers.MIO())
                if (l instanceof com.bitwig.flt.document.core.master.device.zTz layer && i++ == 1) return layer.KAS();
        }
        return null;
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
