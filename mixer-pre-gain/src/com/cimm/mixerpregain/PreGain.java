package com.cimm.mixerpregain;

import bitwig.obf.FAu; // a device chain
import bitwig.obf.jsd; // a device
import bml.api.Bml;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pre gain: each track's first device is Bitwig's Tool, inserted automatically and drawn only as a gain knob (in the
 * mixer strip and at the start of the device panel). Both knobs set Tool's Gain, so they always match.
 */
final class PreGain {
    static final Bml.Mod MOD = Bml.mod("mixer-pre-gain");
    private static final Map<Object, Long> inserting = Collections.synchronizedMap(new WeakHashMap<>());
    private PreGain() {}

    static File toolFile() {
        String root = System.getProperty("installationRoot", "/Applications/Bitwig Studio.app");
        return new File(root, "Contents/Resources/Library/devices/Tool.bwdevice");
    }

    static boolean enabled() { try { return MOD.settings().getBoolean("pre_gain", true); } catch (Throwable t) { return true; } }

    /** Device type (xlG.Coi(): AUDIO_TO_AUDIO, NOTE_TO_NOTE, NOTE_TO_AUDIO = instrument, AUDIO_TO_NOTE). */
    static String type(jsd d) { Object t = Reflect.call(d, "Coi"); return t == null ? "" : t.toString(); }

    private static final java.util.Set<Object> fxChains = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    private static final java.util.Set<String> loggedSlots = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The first instrument (NOTE_TO_AUDIO) in a track chain, or null. */
    static jsd instrument(FAu chain) {
        for (int i = 0; i < chain.rGR(); i++) { jsd d = chain.MRY(i); if (d != null && "NOTE_TO_AUDIO".equals(type(d))) return d; }
        return null;
    }

    /** A Bitwig instrument's FX slot chain (its "FX" slot), or null (plug-ins have none). */
    static FAu fxSlot(jsd instrument) {
        try {
            String[] names = instrument.Guv(); // the device's slot names
            if (names == null) return null;
            if (loggedSlots.add(Params.safeName(instrument))) MOD.log("slots of " + Params.safeName(instrument) + ": " + String.join(", ", names));
            for (String n : names) {
                String l = n.toLowerCase(java.util.Locale.ROOT);
                if (l.contains("fx") && !l.contains("note")) {
                    bitwig.obf.Sas slot = instrument.FzB(n); // the slot
                    FAu c = slot == null ? null : slot.Ucl(); // its device chain
                    if (c != null) { fxChains.add(c); return c; }
                }
            }
        } catch (Throwable t) { MOD.log("fx slot lookup failed", t); }
        return null;
    }

    /** Where the pre gain sits: first in the instrument's FX slot; else right after the instrument; else first in the chain. */
    record Place(FAu chain, int index) {}

    static Place place(FAu track) {
        jsd inst = instrument(track);
        if (inst != null) {
            FAu fx = fxSlot(inst);
            if (fx != null) return new Place(fx, 0);
            for (int i = 0; i < track.rGR(); i++) if (track.MRY(i) == inst) return new Place(track, i + 1);
        }
        return new Place(track, 0);
    }

    static final String NAME = "Pre-Gain";
    static boolean isTool(jsd d) { if (d == null) return false; String n = Params.safeName(d); return "Tool".equals(n) || NAME.equals(n); }

    private static final java.util.Set<jsd> styled = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    /** Once per device: name it "Pre-Gain" and collapse it (its display settings' is_expanded = false). */
    static void style(jsd d) {
        if (d == null || !styled.add(d)) return;
        try { if (!NAME.equals(Params.safeName(d))) ((com.bitwig.flt.document.core.master.device.Dgc) d).Grk(NAME); } // the device's name
        catch (Throwable t) { MOD.log("pre gain: rename failed", t); }
        try {
            Object settings = d.MJs(); // the device's display settings
            bitwig.obf.TZU.FzB.cfM((com.bitwig.ramona.core.oDc) settings, false); // is_expanded: collapsed
        } catch (Throwable t) { MOD.log("pre gain: collapse failed", t); }
    }

    /** The track's pre gain: the Tool in its place. */
    static jsd device(FAu track) {
        try {
            if (track == null) return null;
            Place p = place(track);
            if (p.index() >= p.chain().rGR()) return null;
            jsd d = p.chain().MRY(p.index());
            return isTool(d) ? d : null;
        } catch (Throwable t) { return null; }
    }

    /** The chain's first device that isn't the hidden pre gain. */
    static jsd firstVisible(FAu chain) {
        try {
            jsd pre = device(chain);
            for (int i = 0; i < chain.rGR(); i++) { jsd d = chain.MRY(i); if (d != null && d != pre) return d; }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Whether this device is a track's pre gain. */
    static boolean isPreGain(jsd d) {
        if (d == null || !enabled() || !isTool(d)) return false;
        try {
            FAu chain = d.xL();
            if (chain == null) return false;
            if (fxChains.contains(chain)) return chain.rGR() > 0 && chain.MRY(0) == d; // first in an instrument's FX slot
            return device(chain) == d;
        } catch (Throwable t) { return false; }
    }

    /** Make sure the chain has its pre gain: once per track per session, never if a Tool is already in the chain. */
    static void ensure(Object track, FAu chain) {
        if (chain == null || !enabled() || device(chain) != null) return;
        if (inserting.containsKey(track)) return;
        inserting.put(track, System.currentTimeMillis());
        try {
            Place p = place(chain);
            for (int i = 0; i < p.chain().rGR(); i++) if (isTool(p.chain().MRY(i))) { MOD.log("pre gain: a Tool is already there; not inserting"); return; }
            File f = toolFile();
            if (!f.isFile()) { MOD.log("pre gain: Tool device file not found at " + f); return; }
            int at = p.index();
            var point = new com.bitwig.flt.document.core.iface.clipboard.device.DeviceChainInsertionPoint(p.chain(), at, false, false, null);
            point.cfM(new bitwig.obf.IkS(new com.bitwig.base.io.z2y(f)), bitwig.obf.XuI.cfM.cfM(bitwig.obf.pHE.MRY, false), null);
            MOD.log("pre gain: inserted Tool " + (fxChains.contains(p.chain()) ? "in the instrument's FX slot" : "at " + at));
        } catch (Throwable t) {
            MOD.log("pre gain: insert failed", t);
        }
    }

    // ---- the gain parameter ----
    private static final Map<jsd, Params.P> gains = Collections.synchronizedMap(new WeakHashMap<>());

    static Params.P gain(jsd tool) {
        if (tool == null) return null;
        Params.P cached = gains.get(tool);
        if (cached != null) return cached;
        Params.P g = find(tool);
        if (g != null) gains.put(tool, g);
        return g;
    }

    private static Params.P find(jsd tool) {
        Params p = new Params(tool);
        Params.P g = p.find("contents/amplitude"); // Tool's gain in dB (±36)
        return g;
    }
}
