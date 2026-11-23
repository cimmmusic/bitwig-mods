package com.cimm.clipfx;

import com.bitwig.flt.document.core.master.device.xlG; // a device
import com.bitwig.ramona.core.oDc;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds a clip's effect chain from Bitwig's own devices, loaded from the presets the mod ships (an FX Layer as the
 * container, a Tool as each clip's gate). Stage 2 test: load and insert the container on the clip's track.
 */
final class ChainBuilder {
    private ChainBuilder() {}

    static File preset(String name) {
        try {
            Path dir = ClipFx.MOD.resource("presets");
            File f = dir.resolve(name + ".bwpreset").toFile();
            if (f.isFile()) return f;
        } catch (Throwable t) { ClipFx.MOD.log("presets folder not found", t); }
        File user = new File(System.getProperty("user.home"), "Documents/Bitwig Studio/Library/Presets/"
                + (name.endsWith("Gate") ? "Tool/" : "FX Layer/") + name + ".bwpreset");
        if (!user.isFile()) user = new File(System.getProperty("user.home"), "Documents/Bitwig Studio/Library/Presets/FX Layer/" + name + ".bwpreset");
        return user.isFile() ? user : null;
    }

    /** A device from a preset file, with Bitwig's own preset loader. */
    static xlG load(File preset) throws Exception {
        bitwig.obf.F2 data = bitwig.obf.lUL.cfM(preset);
        return bitwig.obf.zlI.cfM(data, new com.bitwig.flt.document.core.master.IbI(new com.bitwig.base.io.z2y(preset), null, null));
    }

    /** The track's own device chain: the object among the track's getters whose path in the project ends in
     *  "/device_chain" (sWR() gives a detached chain), else the parent of its first device. */
    static Object trackChain(bitwig.obf.xJo track) {
        Class<?> c = track.getClass();
        for (int up = 0; up < 8 && c != null && c != Object.class; up++, c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || !java.lang.reflect.Modifier.isPublic(m.getModifiers())
                        || java.lang.reflect.Modifier.isStatic(m.getModifiers()) || !oDc.class.isAssignableFrom(m.getReturnType())) continue;
                try {
                    Object o = m.invoke(track);
                    String p = Probe.path(o);
                    if (p.endsWith("/device_chain")) { return o; }
                } catch (Throwable ignored) {}
            }
        }
        try { Object first = track.mcL(); return first == null ? null : Probe.parent(first); } catch (Throwable t) { return null; }
    }

    /** The unit's layers (Dry, FX): each layer's chain, its devices, its mixer, and the first device's contents.
     *  Only plain getters: reading a getter that creates objects crashed Bitwig (an unfinished undo step). */
    static void describeUnit(xlG unit) {
        try {
            com.bitwig.flt.document.core.master.device.SaG contents = ((com.bitwig.flt.document.core.master.device.AxS) unit).puu();
            for (Object part : contents.E3()) {
                if (!(part instanceof com.bitwig.flt.document.core.master.device.Dys layers)) continue;
                int i = 0;
                for (Object l : layers.MIO()) {
                    if (!(l instanceof com.bitwig.flt.document.core.master.device.zTz layer)) continue;
                    com.bitwig.flt.document.core.master.device.ISf w = layer.wZs(), k = layer.KAS();
                    StringBuilder b = new StringBuilder("UNIT layer " + i++ + " " + Probe.path(layer) + "\n  mixer " + Probe.path(layer.d3())
                            + "\n  wZs " + Probe.path(w) + " (" + w.HL().size() + " device(s))"
                            + "\n  KAS " + Probe.path(k) + " (" + k.HL().size() + " device(s))");
                    com.bitwig.flt.document.core.master.device.ISf chain = Probe.path(k).endsWith("/device_chain") ? k : w;
                    for (Object d : chain.HL()) {
                        b.append("\n  device ").append(d.getClass().getSimpleName()).append(' ').append(Probe.path(d));
                        if (d instanceof com.bitwig.flt.document.core.master.device.AxS tool) {
                            com.bitwig.flt.document.core.master.device.SaG tc = tool.puu();
                            // cached lists, filled from the device's own children (reading only)
                            for (Object c : tc.ghS()) {
                                if (!(c instanceof bitwig.obf.rce p)) continue; // a parameter
                                b.append("\n    param ").append(Probe.path(p).replaceAll(".*/device_contents/", ""))
                                 .append(" | qFI ").append(call(() -> p.qFI())).append(" | aF_ ").append(call(() -> p.aF_()))
                                 .append(" | Pus ").append(call(() -> p.Pus())).append(" | fFs ").append(call(() -> p.fFs()))
                                 .append(" | ax_ ").append(call(() -> p.ax_()))
                                 .append(" | value ").append(call(() -> ((bitwig.obf.Opy) (Object) p).Grk().getDoubleValue()));
                            }
                        }
                    }
                    ClipFx.MOD.log(b.toString());
                }
            }
        } catch (Throwable t) { ClipFx.MOD.log("describing the unit failed", t); }
    }

    interface Get { Object get() throws Throwable; }
    private static Object call(Get g) { try { return g.get(); } catch (Throwable t) { return t; } }

    private static void listed(StringBuilder b, String name, Object r) {
        if (r instanceof Throwable t) { b.append("\n    ").append(name).append(" ! ").append(t); return; }
        List<?> l = (List<?>) r;
        b.append("\n    ").append(name).append(" ").append(l == null ? "null" : l.size() + "");
        if (l != null) for (int i = 0; i < Math.min(8, l.size()); i++)
            b.append("\n      ").append(l.get(i) == null ? "null" : l.get(i).getClass().getSimpleName() + " " + Probe.path(l.get(i)).replaceAll(".*/device_chain/", ""));
    }

    /** Test: the container at the start of the selected clip's track (one undo step), and what it holds. UI thread. */
    static void test(List<Object> selection) {
        if (selection.isEmpty()) { ClipFx.MOD.log("TEST: nothing selected"); return; }
        Object clip = selection.get(0);
        bitwig.obf.xJo track = null;
        com.bitwig.flt.document.core.master.g2L project = null;
        for (Object x = clip; x != null; x = Probe.parent(x)) {
            if (track == null && x instanceof bitwig.obf.xJo t) track = t;
            if (x instanceof com.bitwig.flt.document.core.master.g2L p) project = p;
        }
        if (track == null || project == null) { ClipFx.MOD.log("TEST: no track / project above the clip"); return; }
        File container = preset("Clip FX Unit"), gate = preset("Clip FX Gate");
        ClipFx.MOD.log("TEST: presets " + container + " | " + gate);
        if (container == null) return;
        try {
            xlG fx = load(container);
            ClipFx.MOD.log("TEST: loaded container " + fx.getClass().getName());
            Object chainObj = trackChain(track);
            ClipFx.MOD.log("TEST: track chain " + (chainObj == null ? "not found" : chainObj.getClass().getName() + " " + Probe.path(chainObj)));
            if (!(chainObj instanceof com.bitwig.flt.document.core.master.device.IPO chain)) {
                return;
            }
            project.cfM("Add Clip FX", false); // one undo step
            try { chain.cfM(fx, chain.HL().size()); } finally { project.cfM(false); } // at the end of the track's chain
            ClipFx.MOD.log("TEST: inserted at " + Probe.path(fx) + "; the track's chain now has " + chain.HL().size() + " device(s)");
            describeUnit(fx);

        } catch (Throwable t) { ClipFx.MOD.log("TEST: building failed", t); }
    }
}
