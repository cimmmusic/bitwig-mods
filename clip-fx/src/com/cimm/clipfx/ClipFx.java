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
    }

    private static final Map<Object, Fx> byObject = Collections.synchronizedMap(new WeakHashMap<>());

    static Fx shown(Object o) { Fx f = o == null ? null : byObject.get(o); return f != null ? f : new Fx(); }

    static Fx edit(Object o) { return byObject.computeIfAbsent(o, k -> new Fx()); }
}
