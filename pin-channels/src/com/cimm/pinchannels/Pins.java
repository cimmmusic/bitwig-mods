package com.cimm.pinchannels;

import bitwig.obf.xJo; // a track
import bml.api.Bml;
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.ramona.core.zjj; // walks a project's objects of one class

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which channels are pinned: track UUIDs, kept in the mod's settings (one per line), in pin order.
 * Track UUIDs are unique, so one list serves every project; a project shows the pins whose tracks it has.
 */
final class Pins {
    static final Bml.Mod MOD = Bml.mod("pin-channels");
    private static final String KEY = "pinned";
    private static Set<UUID> pinned;
    /** The project a bar or a pin action last saw: a new bar sizes itself from it straight away. */
    static volatile java.lang.ref.WeakReference<g2L> lastProject = new java.lang.ref.WeakReference<>(null);

    /** Bumped on every change: bars compare it to know when to rebuild. */
    static volatile int version;

    private Pins() {}

    private static synchronized Set<UUID> load() {
        if (pinned != null) return pinned;
        pinned = new LinkedHashSet<>();
        try {
            for (String s : MOD.settings().get(KEY, "").split("\n")) {
                try { if (!s.isBlank()) pinned.add(UUID.fromString(s.trim())); } catch (IllegalArgumentException ignored) {}
            }
        } catch (Throwable t) { MOD.log("couldn't read pins", t); }
        return pinned;
    }

    private static synchronized void save() {
        List<String> out = new ArrayList<>();
        for (UUID u : pinned) out.add(u.toString());
        try { MOD.settings().put(KEY, String.join("\n", out)); } catch (Throwable t) { MOD.log("couldn't save pins", t); }
        version++;
    }

    /** Whether anything is pinned at all (cheap: painting checks it first). */
    static boolean any() { Set<UUID> p = pinned; return p == null ? !load().isEmpty() : !p.isEmpty(); }

    static synchronized boolean isPinned(xJo t) {
        try { return load().contains(t.kFU()); } catch (Throwable e) { return false; }
    }

    static synchronized void pin(xJo t, boolean on) {
        try {
            UUID id = t.kFU();
            if (id == null) return;
            try { if (t.cb_() != null) lastProject = new java.lang.ref.WeakReference<>(t.cb_()); } catch (Throwable ignored) {}
            boolean changed = on ? load().add(id) : load().remove(id);
            if (changed) { save(); MOD.log((on ? "pinned '" : "unpinned '") + t.ca_() + "'"); }
        } catch (Throwable e) { MOD.log("pin failed", e); }
    }

    /** The project's pinned tracks, in pin order. */
    static List<xJo> tracks(g2L project) {
        if (project == null) return Collections.emptyList();
        Set<UUID> ids;
        synchronized (Pins.class) { ids = new LinkedHashSet<>(load()); }
        if (ids.isEmpty()) return Collections.emptyList();
        java.util.Map<UUID, xJo> byId = new java.util.HashMap<>();
        try {
            zjj.MRY(project, xJo.class, null, o -> {
                xJo t = (xJo) o;
                try { UUID u = t.kFU(); if (u != null && ids.contains(u)) byId.put(u, t); } catch (Throwable ignored) {}
            });
        } catch (Throwable e) { MOD.log("couldn't list tracks", e); }
        List<xJo> out = new ArrayList<>();
        for (UUID u : ids) { xJo t = byId.get(u); if (t != null) out.add(t); }
        return out;
    }
}
