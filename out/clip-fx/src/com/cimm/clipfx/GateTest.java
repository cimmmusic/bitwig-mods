package com.cimm.clipfx;

import com.bitwig.flt.document.core.master.device.AxS;
import com.bitwig.flt.document.core.master.device.ISf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stage 3 test: write the FX gate as clip automation with Bitwig's own operation
 * insert_arranger_clip_automation_events(parameter, times, normalized_values, interpolation_mode, curvatures,
 * should_adjust_previous_curvature, clip, clip_loop_iteration, selection), called on the clip's track.
 */
final class GateTest {
    private GateTest() {}

    static void run(List<Object> selection) {
        if (selection.isEmpty()) { ClipFx.MOD.log("GATE: nothing selected"); return; }
        Object clip = selection.get(0);
        bitwig.obf.xJo track = null;
        com.bitwig.flt.document.core.master.g2L project = null;
        for (Object x = clip; x != null; x = Probe.parent(x)) {
            if (track == null && x instanceof bitwig.obf.xJo t) track = t;
            if (x instanceof com.bitwig.flt.document.core.master.g2L p) project = p;
        }
        if (track == null || project == null) { ClipFx.MOD.log("GATE: no track / project"); return; }
        try {
            // the clip's timing values (for where to write)
            StringBuilder t = new StringBuilder("GATE clip " + clip.getClass().getSimpleName() + " " + Probe.path(clip) + " timing:");
            for (java.lang.reflect.Method m : clip.getClass().getMethods())
                if (m.getParameterCount() == 0 && m.getReturnType() == double.class && !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                    try { t.append(' ').append(m.getName()).append('=').append(m.invoke(clip)); } catch (Throwable ignored) {}
            ClipFx.MOD.log(t.toString());

            Object chainObj = ChainBuilder.trackChain(track);
            if (!(chainObj instanceof ISf chain)) { ClipFx.MOD.log("GATE: the track's chain wasn't found"); return; }
            AxS unit = null;
            bitwig.obf.rce[] vol = new bitwig.obf.rce[2];
            if (!chain.HL().isEmpty() && chain.HL().get(chain.HL().size() - 1) instanceof AxS last) { vol = volumes(last); if (vol[1] != null) unit = last; }
            if (unit == null) { // no unit at the end of the track yet: add one (its own undo step)
                java.io.File preset = ChainBuilder.preset("Clip FX Unit");
                if (preset == null) { ClipFx.MOD.log("GATE: Clip FX Unit preset not found"); return; }
                com.bitwig.flt.document.core.master.device.xlG u = ChainBuilder.load(preset);
                project.cfM("Add Clip FX", false);
                try { chain.cfM(u, chain.HL().size()); } finally { project.cfM(false); }
                unit = (AxS) u;
                vol = volumes(unit);
                ClipFx.MOD.log("GATE: added a unit at " + Probe.path(unit));
            }
            ClipFx.MOD.log("GATE: unit " + Probe.path(unit) + " dry volume " + (vol[0] != null) + ", fx volume " + (vol[1] != null));
            if (vol[1] == null) return;

            double start = ((bitwig.obf.TWN) clip).JhA();
            bitwig.obf.jRs[] modes = bitwig.obf.jRs.values();
            ClipFx.MOD.log("GATE: interpolation modes " + Arrays.toString(modes));
            List<Object> args = new ArrayList<>(Arrays.asList(
                    ((bitwig.obf.bDH) (Object) vol[1]).Mph(), // parameter
                    new double[]{start, start + 1},          // times
                    new double[]{1, 1},                      // normalized_values: FX volume open
                    modes[0],                                // interpolation_mode
                    new double[]{0, 0},                      // curvatures
                    Boolean.FALSE,                           // should_adjust_previous_curvature
                    clipRef(project, clip),                  // clip (the transport's view of it)
                    0,                                       // clip_loop_iteration
                    null));                                  // selection
            // by name: getter names differ between Bitwig versions, operation names don't
            Object found = ((bitwig.obf.der) (Object) bitwig.obf.BTS.cfM).cfM("insert_arranger_clip_automation_events");
            if (!(found instanceof bitwig.obf.deV op)) { ClipFx.MOD.log("GATE: operation not found (" + found + ")"); return; }
            project.cfM("Clip FX gate", false);
            try {
                // the operation belongs to one of the clip's owners (lane, arrangement, track...): the first that has it
                boolean done = false;
                // the operation is on the track's automation lanes component (schema 'automation_lanes', owner 'track')
                final bitwig.obf.xJo tr = track;
                List<Object> owners = new ArrayList<>();
                // only an automation lanes object that is attached to the track (its path ends in /automation_lanes):
                // a detached one has no owner, and Bitwig's implementation fails on it
                for (String g : new String[]{"meo", "iSC"}) {
                    try {
                        Object lanes = g.equals("meo") ? tr.meo() : tr.iSC();
                        String lp = Probe.path(lanes);
                        ClipFx.MOD.log("GATE: track." + g + "() = " + (lanes == null ? "null" : lanes.getClass().getSimpleName() + " '" + lp + "'"));
                        if (lp.endsWith("automation_lanes") && !owners.contains(lanes)) owners.add(lanes);
                    } catch (Throwable e) { ClipFx.MOD.log("GATE: track." + g + "() failed: " + e); }
                }
                if (owners.isEmpty()) { ClipFx.MOD.log("GATE: no attached automation lanes found"); return; }
                for (Object owner : owners) {
                    if (done || !(owner instanceof com.bitwig.ramona.core.oDc)) continue;
                    try {
                        Object r = op.cfM((com.bitwig.ramona.core.oDc) owner, args);
                        ClipFx.MOD.log("GATE: written on " + owner.getClass().getSimpleName() + " " + Probe.path(owner) + ", result " + r);
                        done = true;
                    } catch (IllegalStateException e) {
                        ClipFx.MOD.log("GATE: not on " + owner.getClass().getSimpleName() + " (" + e.getMessage() + ")");
                    }
                }
            }
            finally { project.cfM(false); }
        } catch (Throwable e) { ClipFx.MOD.log("GATE: failed", e); }
    }

    /** The clip as the automation operation wants it: the transport's la1 for the clip. */
    static Object clipRef(com.bitwig.flt.document.core.master.g2L project, Object clip) {
        try {
            Object transport = ((com.bitwig.flt.document.core.iface.pHd) project).ifN();
            Object r = ((bitwig.obf.SEJ) transport).cfM((bitwig.obf.Ob) clip);
            ClipFx.MOD.log("GATE: clip reference " + (r == null ? "null" : r.getClass().getSimpleName() + " " + Probe.path(r)));
            return r;
        } catch (Throwable t) { ClipFx.MOD.log("GATE: clip reference failed", t); return null; }
    }

    /** {Dry Tool Volume, FX Tool Volume} of a unit (layer 0 = Dry, layer 1 = FX). */
    static bitwig.obf.rce[] volumes(AxS unit) {
        bitwig.obf.rce[] out = new bitwig.obf.rce[2];
        for (Object part : unit.puu().E3()) {
            if (!(part instanceof com.bitwig.flt.document.core.master.device.Dys layers)) continue;
            int i = 0;
            for (Object l : layers.MIO()) {
                if (!(l instanceof com.bitwig.flt.document.core.master.device.zTz layer) || i > 1) continue;
                ISf chain = layer.KAS();
                if (!chain.HL().isEmpty() && chain.HL().get(0) instanceof AxS tool)
                    for (Object c : tool.puu().ghS())
                        if (c instanceof bitwig.obf.rce p && "Volume".equals(p.aF_())) out[i] = p;
                i++;
            }
        }
        return out;
    }
}
