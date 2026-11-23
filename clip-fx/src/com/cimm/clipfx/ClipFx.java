package com.cimm.clipfx;

import bml.api.Bml;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Each clip's (or audio event's) Clip FX setting, by Bitwig's own object. Stage 1: kept while working only. */
final class ClipFx {
    private ClipFx() {}

    static final Bml.Mod MOD = Bml.mod("clip-fx");

    static final class Fx {
        boolean on;
        Object unit;     // the clip's Clip FX Unit on its track
        double start = Double.NaN, end = Double.NaN; // where its gate is written (beats)
        String path;     // the clip's path at the last undo step (to find it again when Bitwig re-creates it)
        String name; double length = Double.NaN;      // the clip's name and length then
    }

    private static final Map<Object, Fx> byObject = Collections.synchronizedMap(new WeakHashMap<>());

    static Fx shown(Object o) {
        if (o == null) return new Fx();
        Fx f = byObject.get(o);
        if (f != null && f.unit != null && Probe.parent(f.unit) == null) { byObject.remove(o); f = null; } // its unit was undone / deleted
        if (f == null && checked.put(o, Boolean.TRUE) == null) { // stored in the project (opening it)
            f = Store.restore(o);
            if (f != null) byObject.put(o, f);
        }
        return f != null ? f : new Fx();
    }

    static void forget(Object clip) { byObject.remove(clip); checked.remove(clip); } // an Undo can bring it back: looked up again

    /** The setting now belongs to another clip object (Bitwig re-created the clip when it moved). */
    static void moveTo(Object from, Object to, Fx f) { byObject.remove(from); byObject.put(to, f); checked.put(to, Boolean.TRUE); }

    static boolean has(Object clip) { return byObject.containsKey(clip); }

    /** Clips already looked up in the project's stored settings. */
    private static final Map<Object, Boolean> checked = Collections.synchronizedMap(new WeakHashMap<>());

    /** A copy of every clip's setting (for storing). */
    static java.util.List<Map.Entry<Object, Fx>> entries() {
        synchronized (byObject) { return new java.util.ArrayList<>(new java.util.HashMap<>(byObject).entrySet()); }
    }

    static Fx edit(Object o) { Fx f = shown(o); byObject.putIfAbsent(o, f); return byObject.get(o); }

    /** Every Clip FX Unit the mod made (to know whether a device is one of a clip's inserts). */
    static final java.util.Set<Object> units = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** Whether a device is an insert of a clip: inside a unit made by the mod. */
    static boolean isInsert(Object device) {
        for (Object x = Probe.parent(device); x != null; x = Probe.parent(x)) if (units.contains(x)) return true;
        return false;
    }
}
