package com.cimm.groove;

import com.bitwig.ramona.core.oDc; // an object of the project (note, clip, track ...): J_() is its parent

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Grooves of their own for notes, clips and tracks (set in the Inspector's Groove block). When a note is packed for
 * playback, the note itself, then each of its parents (its clip, then its track ...) is looked up: the first one with
 * a groove of its own decides, whatever the global groove is (on, off, or Bitwig's own). "Off" plays that note, clip or
 * track straight. Nothing found: the global groove, as before.
 *
 * Kept by object while working. (Stored in the project: next step; for now the log shows what identifies them.)
 */
final class Overrides {
    private Overrides() {}

    static final int DEFAULT = 0, OFF = 1, FILE = 2, MANUAL_A = 3; // MANUAL_A + slot
    static final String[] SOURCES = {"Default", "Off", "Groove File", "Manual A", "Manual B", "Manual C", "Manual D"};

    static final class Ov {
        int source = DEFAULT;
        String file; // the groove file's path (Groove File)
        Groove.Amounts amounts = State.fileDefaults();

        Ov copy() { Ov o = new Ov(); o.source = source; o.file = file; o.amounts = State.copy(amounts); return o; }
    }

    /** What a note plays with: a groove (null: straight) and its amounts. */
    static final class Res {
        final Groove groove; final Groove.Amounts amounts; final Object owner;
        Res(Groove groove, Groove.Amounts amounts, Object owner) { this.groove = groove; this.amounts = amounts; this.owner = owner; }
    }

    private static final Map<Object, Ov> byObject = Collections.synchronizedMap(new WeakHashMap<>());

    static boolean any() { return !byObject.isEmpty() || !pending.isEmpty(); }

    static Ov get(Object o) {
        if (o == null) return null;
        Ov v = byObject.get(o);
        return v != null || pending.isEmpty() ? v : claim(o);
    }

    // ---- stored in the project: each note, clip (and its content) and track by its path in the project
    //      ("float_core.float_document/track_group/owned_main_tracks#0/.../events#2"). Paths change as notes and clips
    //      come and go, so they are written again inside every Bitwig undo step (ProjectStore.refresh), never late.
    //      On opening, they wait in 'pending' until Bitwig touches the object (playback, painting, Inspector). ----

    private static final Map<String, Ov> pending = Collections.synchronizedMap(new java.util.HashMap<>());
    /** Objects already looked up in 'pending' (each is looked up once). */
    private static final Map<Object, Boolean> checked = Collections.synchronizedMap(new WeakHashMap<>());

    static String path(Object o) {
        try { return o instanceof oDc d && d.J_() != null ? d.veO() : null; } catch (Throwable t) { return null; }
    }

    /** A stored setting waiting for this object, if any (then it's the object's from now on). */
    private static Ov claim(Object o) {
        if (checked.put(o, Boolean.TRUE) != null) return null;
        String p = path(o);
        Ov v = p == null ? null : pending.remove(p);
        if (v != null) {
            byObject.put(o, v);
            if (claimLogs++ < 12) State.MOD.log("groove restored on " + kind(o) + " " + p);
        }
        return v;
    }
    private static int claimLogs;

    static void dump(java.util.Properties p) {
        int n = 0;
        Map<String, Ov> out = new java.util.LinkedHashMap<>();
        synchronized (byObject) {
            for (Map.Entry<Object, Ov> e : byObject.entrySet()) {
                if (e.getValue().source == DEFAULT) continue;
                String path = path(e.getKey());
                if (path != null) out.put(path, e.getValue()); // a deleted note or clip has no parent: dropped
            }
        }
        synchronized (pending) { for (Map.Entry<String, Ov> e : pending.entrySet()) out.putIfAbsent(e.getKey(), e.getValue()); }
        for (Map.Entry<String, Ov> e : out.entrySet()) {
            Ov v = e.getValue(); Groove.Amounts a = v.amounts;
            String k = "ov." + n++ + ".";
            p.setProperty(k + "path", e.getKey());
            p.setProperty(k + "source", "" + v.source);
            if (v.file != null) p.setProperty(k + "file", v.file);
            p.setProperty(k + "amounts", a.strength + "," + a.timing + "," + a.velocity + "," + a.random + "," + a.quantize + "," + a.base);
        }
    }

    /** The project's stored settings (opening it, switching to it, Undo / Redo of a groove change). */
    static void restore(java.util.Properties p) {
        byObject.clear(); checked.clear(); pending.clear();
        for (int n = 0; ; n++) {
            String k = "ov." + n + ".", path = p.getProperty(k + "path");
            if (path == null) break;
            try {
                Ov v = new Ov();
                v.source = Integer.parseInt(p.getProperty(k + "source", "0"));
                v.file = p.getProperty(k + "file");
                String[] a = p.getProperty(k + "amounts", "").split(",");
                if (a.length == 6) {
                    v.amounts = new Groove.Amounts();
                    v.amounts.strength = Double.parseDouble(a[0]); v.amounts.timing = Double.parseDouble(a[1]);
                    v.amounts.velocity = Double.parseDouble(a[2]); v.amounts.random = Double.parseDouble(a[3]);
                    v.amounts.quantize = Double.parseDouble(a[4]); v.amounts.base = Integer.parseInt(a[5]);
                }
                pending.put(path, v);
            } catch (Throwable t) { State.MOD.log("couldn't read a stored groove: " + path, t); }
        }
        if (!pending.isEmpty()) State.MOD.log(pending.size() + " note / clip / track groove(s) stored in the project");
    }

    /** A project without stored settings (a new one). */
    static void clear() { byObject.clear(); checked.clear(); pending.clear(); }

    /** The setting shown for an object: its own, or a fresh Default one (not stored until changed). */
    static Ov shown(Object o) { Ov v = get(o); if (v != null) { Object c = content(o); if (c != null && byObject.get(c) != v) byObject.put(c, v); } return v != null ? v : new Ov(); }

    /** Changes each object's setting (creating it as needed); Default with nothing else set is removed. */
    static void edit(List<Object> objects, java.util.function.Consumer<Ov> change) {
        for (Object o : objects) {
            if (o == null) continue;
            Ov v = byObject.get(o);
            if (v == null) { v = new Ov(); byObject.put(o, v); }
            change.accept(v);
            // a clip (arranger or launcher) is a reference to its content, which is what its notes belong to: the
            // content shares the clip's setting
            Object content = content(o);
            if (content != null && byObject.get(content) != v) byObject.put(content, v);
            if (v.source == FILE && v.file == null) { State.Entry e = State.selected(); if (e != null) v.file = e.path.toString(); }
        }
        State.overridesChanged();
        Hooks.repaintMarks();
    }

    private static int copyLogs;

    /**
     * Bitwig copied a value from one object to another (copy / paste, duplicate, Alt-drag): a note copied from a note
     * with a groove of its own gets the same groove. Called for every copied number, so it returns at once otherwise.
     */
    static void copied(Object to, Object from) {
        if (byObject.isEmpty() || to == from || from == null || to == null || from.getClass() != to.getClass()) return;
        boolean note = from instanceof bitwig.obf.n1, clip = from instanceof bitwig.obf.hnT;
        if (!note && !clip) return;
        Ov v = get(from);
        if (v == null || byObject.containsKey(to)) return;
        byObject.put(to, v.copy());
        if (copyLogs++ < 10) State.MOD.log("groove copied with a " + (note ? "note" : "clip") + " (" + SOURCES[v.source] + ")");
        if (!repackQueued) {
            repackQueued = true;
            try { com.bitwig.flt.app.CHR.Grk().asyncExec(() -> { repackQueued = false; linkContents(); State.overridesChanged(); Hooks.repaintMarks(); }); }
            catch (Throwable t) { repackQueued = false; }
        }
    }
    private static volatile boolean repackQueued;

    /** The first override on a note or its parents, or null (the global groove applies). */
    static Res resolve(Object note) {
        if (!any() || note == null) return null;
        Object o = note;
        for (int depth = 0; depth < 16 && o != null; depth++) {
            Ov v = get(o);
            if (v != null && v.source != DEFAULT) return new Res(groove(v), v.amounts, o);
            o = parent(o);
        }
        return null;
    }

    /** Every clip with a setting shares it with its content (a pasted clip gets its content after its values). */
    static void linkContents() {
        Object[][] pairs;
        synchronized (byObject) {
            List<Object[]> l = new ArrayList<>();
            for (Map.Entry<Object, Ov> e : byObject.entrySet()) if (e.getKey() instanceof bitwig.obf.hnT) l.add(new Object[]{e.getKey(), e.getValue()});
            pairs = l.toArray(new Object[0][]);
        }
        for (Object[] p : pairs) {
            Object content = content(p[0]);
            if (content != null && byObject.get(content) != p[1]) byObject.put(content, (Ov) p[1]);
        }
    }

    /**
     * Whether a clip plays a groove of its own anywhere: its own, its track's (or a parent's), or one on a note or audio
     * event inside it. Bitwig's own shuffle is then kept off for that clip.
     */
    static boolean playsOwnGroove(Object clip) {
        if (!any() || clip == null) return false;
        for (Object x = clip; x != null; x = parent(x)) { Ov v = get(x); if (v != null && v.source != DEFAULT) return true; }
        Object content = content(clip);
        if (content == null) content = clip; // the switch may be on the content itself
        Ov cv = get(content);
        if (cv != null && cv.source != DEFAULT) return true;
        Object[] keys; synchronized (byObject) { keys = byObject.keySet().toArray(); }
        for (Object k : keys) {
            Ov v = byObject.get(k);
            if (v == null || v.source == DEFAULT || k == content) continue;
            Object x = k;
            for (int d = 0; d < 8 && x != null; d++) { if (x == content) return true; x = parent(x); }
        }
        synchronized (pending) { for (String p : pending.keySet()) { String cp = path(content); if (cp != null && p.startsWith(cp + "/")) return true; } }
        return false;
    }

    /** A clip's content (the object its notes belong to), or null for anything else. */
    static Object content(Object o) {
        try { return o instanceof bitwig.obf.hnT clip ? clip.IwV() : null; } catch (Throwable t) { return null; }
    }

    static Object parent(Object o) {
        try { return o instanceof oDc d ? d.J_() : null; } catch (Throwable t) { return null; }
    }

    /** The groove an override plays (null: straight, also for a groove file that isn't loaded). */
    static Groove groove(Ov v) {
        if (v.source == OFF || v.source == DEFAULT) return null;
        if (v.source == FILE) {
            for (State.Entry e : State.list()) if (e.path.toString().equals(v.file)) return e.groove;
            return null;
        }
        int slot = v.source - MANUAL_A;
        return slot >= 0 && slot < State.SLOT_NAMES.length ? State.manualGroove(slot, v.amounts.base) : null;
    }

    /** A few words on what an object plays with, for the Inspector ("Global: Tempest 16 Swing 60"). */
    static String describe(Object o) {
        Object x = o;
        for (int depth = 0; depth < 16 && x != null; depth++) {
            Ov v = get(x);
            if (v != null && v.source != DEFAULT) {
                String what = v.source == OFF ? "straight (Off)" : v.source == FILE ? name(v.file) : SOURCES[v.source];
                return (x == o ? "" : "From " + kind(x) + ": ") + what;
            }
            x = parent(x);
        }
        State.PSnap sn = State.snap(null);
        Groove g = sn.active();
        return "Global: " + (g == null ? (sn.usesGroove() ? "off" : "Bitwig's own") : g.name);
    }

    private static String name(String file) {
        if (file == null) return "no groove file";
        String n = java.nio.file.Path.of(file).getFileName().toString();
        return n.replaceFirst("(?i)\\.(agr|midi?)$", "");
    }

    private static String kind(Object o) {
        if (o instanceof bitwig.obf.xJo || o instanceof com.bitwig.flt.document.core.master.cXR) return "track";
        if (o instanceof bitwig.obf.n1) return "note";
        return "clip";
    }
}
