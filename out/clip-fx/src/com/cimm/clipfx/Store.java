package com.cimm.clipfx;

import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.ramona.core.oDc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clip FX settings live in the project, so they are saved with it: a tagged block in the master track's comment,
 * listing each clip (by its path in the project) with its unit (by path) and On / Off. Paths change as clips and
 * devices come and go, so the block is written again inside every Bitwig undo step (never late, never its own step).
 * The block sits before Global Groove's block, which keeps everything before its own.
 */
public final class Store {
    private Store() {}

    static final String MARK = "\n\n[Clip FX settings, do not edit]\n";
    static final String GROOVE_MARK = "\n\n[Global Groove settings, do not edit]\n";

    // ---------------------------------------------------------------- writing (inside every undo step)

    private static boolean refreshing;
    private static int fails;
    /** Projects that had Clip FX settings written (to clear the block when the last one goes). */
    private static final java.util.Set<Object> written = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Called from MixerHide's hook (the mod's hooks all live in one class). */
    static void inStep() {
        if (refreshing) return;
        refreshing = true;
        try {
            // a deleted clip takes its Clip FX with it, in the same undo step (Undo brings both back)
            for (Map.Entry<Object, ClipFx.Fx> e : ClipFx.entries()) {
                ClipFx.Fx f = e.getValue();
                if (f.unit == null || path(e.getKey()) != null || path(f.unit) == null) continue;
                try {
                    String was = path(f.unit);
                    if (ClipGate.deleteInStep(f.unit)) {
                        ClipFx.forget(e.getKey());
                        ClipFx.MOD.log("Clip FX: the clip was deleted: removed its unit " + was);
                    }
                } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: removing a deleted clip's unit failed", t); }
            }
            Map<g2L, StringBuilder> byProject = new HashMap<>();
            for (Map.Entry<Object, ClipFx.Fx> e : ClipFx.entries()) {
                ClipFx.Fx f = e.getValue();
                String clip = path(e.getKey()), unit = path(f.unit);
                g2L p = project(f.unit);
                if (clip == null || unit == null || p == null) continue; // deleted: dropped
                byProject.computeIfAbsent(p, k -> new StringBuilder()).append(clip).append('\t').append(unit).append('\t').append(f.on ? 1 : 0).append('\n');
            }
            java.util.Set<Object> all = new java.util.HashSet<>(byProject.keySet());
            synchronized (written) { all.addAll(written); }
            for (Object o : all) {
                g2L p = (g2L) o;
                StringBuilder b = byProject.get(p);
                write(p, b == null ? null : b.toString());
                synchronized (written) { if (b == null) written.remove(p); else written.add(p); }
            }
        } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: storing the settings failed", t); }
        finally { refreshing = false; }
    }

    private static void write(g2L p, String blob) {
        com.bitwig.flt.document.core.master.cXR m = p.PEn(); // the master track
        if (m == null) return;
        String c = m.AgD(); if (c == null) c = "";
        int g = c.indexOf(GROOVE_MARK);
        String head = g < 0 ? c : c.substring(0, g), tail = g < 0 ? "" : c.substring(g);
        int k = head.lastIndexOf(MARK);
        String user = k < 0 ? head : head.substring(0, k);
        String text = user + (blob == null || blob.isEmpty() ? "" : MARK + Base64.getEncoder().encodeToString(blob.getBytes(StandardCharsets.UTF_8))) + tail;
        if (!text.equals(c)) m.TtY(text);
    }

    // ---------------------------------------------------------------- reading (opening a project)

    private static final class Rec { String unit; boolean on; }
    /** Per project: the comment last read, and its records by clip path. */
    private static final Map<Object, Object[]> read = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static Map<String, Rec> records(g2L p) {
        com.bitwig.flt.document.core.master.cXR m = p.PEn();
        String c = m == null ? "" : m.AgD(); if (c == null) c = "";
        Object[] cached = read.get(p);
        if (cached != null && c.equals(cached[0])) { @SuppressWarnings("unchecked") Map<String, Rec> r = (Map<String, Rec>) cached[1]; return r; }
        Map<String, Rec> out = new HashMap<>();
        int g = c.indexOf(GROOVE_MARK);
        String head = g < 0 ? c : c.substring(0, g);
        int k = head.lastIndexOf(MARK);
        if (k >= 0) {
            try {
                String blob = new String(Base64.getDecoder().decode(head.substring(k + MARK.length()).trim()), StandardCharsets.UTF_8);
                for (String line : blob.split("\n")) {
                    String[] f = line.split("\t");
                    if (f.length < 3) continue;
                    Rec r = new Rec(); r.unit = f[1]; r.on = f[2].equals("1");
                    out.put(f[0], r);
                }
            } catch (Throwable t) { ClipFx.MOD.log("Clip FX: couldn't read the stored settings", t); }
        }
        read.put(p, new Object[]{c, out});
        if (!out.isEmpty() && cached == null) ClipFx.MOD.log("Clip FX: " + out.size() + " clip(s) with Clip FX stored in the project");
        return out;
    }

    /** The stored setting of a clip, with its unit found on the clip's track, or null. UI thread. */
    static ClipFx.Fx restore(Object clip) {
        try {
            String cp = path(clip);
            g2L p = project(clip);
            if (cp == null || p == null) return null;
            Rec r = records(p).get(cp);
            if (r == null) return null;
            bitwig.obf.xJo track = null;
            for (Object x = clip; x != null; x = Probe.parent(x)) if (x instanceof bitwig.obf.xJo t) { track = t; break; }
            if (track == null || !(ChainBuilder.trackChain(track) instanceof com.bitwig.flt.document.core.master.device.ISf chain)) return null;
            for (Object d : new ArrayList<>(chain.HL())) {
                if (r.unit.equals(path(d))) {
                    ClipFx.Fx f = new ClipFx.Fx(); f.on = r.on; f.unit = d;
                    ClipFx.units.add(d);
                    return f;
                }
            }
        } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: restoring a clip's setting failed", t); }
        return null;
    }

    /** Projects whose stored clips were all loaded (by the comment they were loaded from). */
    private static final Map<Object, String> loadedAll = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Every clip stored with Clip FX in this project, found by its path and given its setting now (not only when it
     * is selected), so deleting several clips at once also removes their units. Called when a Clip FX block is
     * shown (before any edit can shift the paths); once per stored block. UI thread.
     */
    static void loadAll(g2L p) {
        try {
            com.bitwig.flt.document.core.master.cXR m = p.PEn();
            String c = m == null ? "" : m.AgD(); if (c == null) c = "";
            if (c.equals(loadedAll.get(p))) return;
            loadedAll.put(p, c);
            int n = 0;
            for (String clipPath : records(p).keySet()) {
                Object clip = resolve(p, clipPath);
                if (clip == null) continue;
                ClipFx.Fx f = ClipFx.shown(clip); // restores it (when not known yet)
                if (f.unit != null) n++;
            }
            if (n > 0) ClipFx.MOD.log("Clip FX: " + n + " stored clip(s) loaded");
        } catch (Throwable t) { if (fails++ < 3) ClipFx.MOD.log("Clip FX: loading the stored clips failed", t); }
    }

    /** The object at a path ("float_core.float_document/track_group/owned_main_tracks#2/..."), from the project. */
    static Object resolve(g2L p, String path) {
        String root = path(p);
        if (root == null) root = "float_core.float_document";
        if (!path.startsWith(root + "/")) return null;
        Object o = p;
        for (String seg : path.substring(root.length() + 1).split("/")) {
            int hash = seg.lastIndexOf('#');
            String name = hash < 0 ? seg : seg.substring(0, hash);
            int index = hash < 0 ? -1 : Integer.parseInt(seg.substring(hash + 1));
            if (!(o instanceof oDc d)) return null;
            Object atom = ((bitwig.obf.der) (Object) d.ng_()).cfM(name);
            if (index >= 0 && atom instanceof bitwig.obf.deT list) o = list.cfM(d, index);
            else if (atom instanceof bitwig.obf.deU one) o = one.MRY(d);
            else return null;
            if (o == null) return null;
        }
        String got = path(o);
        return path.equals(got) ? o : null;
    }

    // ---------------------------------------------------------------- helpers

    static String path(Object o) {
        try { return o instanceof oDc d && d.J_() != null ? d.veO() : null; } catch (Throwable t) { return null; }
    }

    static g2L project(Object o) {
        for (Object x = o; x != null; x = Probe.parent(x)) if (x instanceof g2L p) return p;
        return null;
    }
}
