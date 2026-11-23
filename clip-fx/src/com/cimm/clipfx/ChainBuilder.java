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
        return user.isFile() ? user : null;
    }

    /** A device from a preset file, with Bitwig's own preset loader. */
    static xlG load(File preset) throws Exception {
        bitwig.obf.F2 data = bitwig.obf.lUL.cfM(preset);
        return bitwig.obf.zlI.cfM(data, new com.bitwig.flt.document.core.master.IbI(new com.bitwig.base.io.z2y(preset), null, null));
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
        File container = preset("Clip FX Container"), gate = preset("Clip FX Gate");
        ClipFx.MOD.log("TEST: presets " + container + " | " + gate);
        if (container == null) return;
        try {
            xlG fx = load(container);
            ClipFx.MOD.log("TEST: loaded container " + fx.getClass().getName());
            com.bitwig.flt.document.core.master.device.ISf chain = track.sWR();
            project.cfM("Add Clip FX", false); // one undo step
            try { chain.cfM(fx, 0); } finally { project.cfM(false); }
            ClipFx.MOD.log("TEST: inserted; the track's chain now has " + chain.HL().size() + " device(s)");
            Probe.describe("container", fx, 1);
            if (gate != null) { xlG g = load(gate); ClipFx.MOD.log("TEST: loaded gate " + g.getClass().getName()); Probe.describe("gate", g, 1); }
        } catch (Throwable t) { ClipFx.MOD.log("TEST: building failed", t); }
    }
}
