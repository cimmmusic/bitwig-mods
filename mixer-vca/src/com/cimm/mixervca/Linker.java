package com.cimm.mixervca;

import bitwig.obf.hSh; // a settable parameter (the mixer's volume is one)
import bitwig.obf.xJo; // a track
import com.bitwig.flt.app.CHR; // Bitwig's application (asyncExec runs on the UI thread)
import com.bitwig.flt.document.core.master.g2L; // a project
import com.bitwig.ramona.core.zjj; // walks a project's objects of one class
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Keeps every VCA's members following its fader, like a console VCA: when the VCA moves by N dB, each member's own
 * fader moves by N dB. Each member keeps a "virtual" level (its fader as if nothing were clamped), so a member pushed
 * past +6 dB or down to −∞ comes back exactly when the VCA returns; moving a member by hand sets its level relative
 * to the VCA from then on.
 *
 * The mixer's volume value is the cube root of the amplitude (0 dB = 1): dB = 60·log10(value). Members are written
 * with the parameter's raw setter (d_, no undo step of its own), so Undo only sees the VCA's move — and undoing it
 * moves the VCA back, which brings the members back with it.
 *
 * Runs ~60×/s on the UI thread; the projects' tracks are re-read twice a second (new VCAs, deleted or added tracks).
 */
final class Linker {
    private Linker() {}

    static final double FLOOR_DB = -200; // −∞ as a number, so moves into and out of silence keep their sizes

    static final class Member {
        final xJo track; final hSh vol;
        double virtualDb = Double.NaN, lastSet = Double.NaN;
        Member(xJo track, hSh vol) { this.track = track; this.vol = vol; }
    }

    static final class Link {
        final xJo vca; final hSh vol;
        double lastDb = Double.NaN;
        final Map<xJo, Member> members = new IdentityHashMap<>();
        Link(xJo vca, hSh vol) { this.vca = vca; this.vol = vol; }
    }

    private static final Set<g2L> projects = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<xJo, Link> links = new IdentityHashMap<>();
    private static volatile boolean queued;
    private static boolean started, loggedScan = true; // the application-list scan finds no projects (library metadata): projects come from the mixer and the menu
    private static long nextScan;

    static synchronized void start() {
        if (started) return;
        started = true;
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cimm-vca"); t.setDaemon(true); return t; })
                 .scheduleAtFixedRate(Linker::tick, 200, 16, TimeUnit.MILLISECONDS);
    }

    static void know(g2L project) { if (project != null) synchronized (projects) { projects.add(project); } nextScan = 0; }

    private static void tick() {
        if (queued) return;
        queued = true;
        try {
            CHR.Grk().asyncExec(() -> {
                queued = false;
                try { step(); } catch (Throwable t) { Vca.MOD.log("VCA update failed", t); }
            });
        } catch (Throwable t) { queued = false; }
    }

    private static void step() {
        long now = System.currentTimeMillis();
        if (now >= nextScan) { nextScan = now + 500; scan(); }
        for (Link l : links.values()) follow(l);
    }

    // ---- finding the VCAs ----

    static List<xJo> allTracks(g2L project) {
        List<xJo> out = new ArrayList<>();
        zjj.MRY(project, xJo.class, null, o -> out.add((xJo) o));
        return out;
    }

    /** The open projects: Bitwig's open documents (found by looking at the application's lists), plus any seen via a menu. */
    private static List<g2L> openProjects() {
        StringBuilder seen = loggedScan ? null : new StringBuilder("VCA: looking for projects:");
        try {
            CHR app = CHR.Grk();
            for (List<?> list : new List<?>[] {app.QWG(), app.xcy()}) {
                if (list == null) continue;
                for (Object o : list) {
                    if (seen != null) seen.append(' ').append(o == null ? "null" : o.getClass().getName());
                    Object p = o;
                    if (o instanceof bitwig.obf.aBF doc) p = doc.TMF();              // a document's project
                    else { Object q = Reflect.call(o, "mtS"); if (q != null) p = q; } // a window's project
                    if (p instanceof g2L g) know(g);
                }
            }
        } catch (Throwable t) { if (seen != null) seen.append(" failed: ").append(t); }
        List<g2L> out;
        synchronized (projects) { out = new ArrayList<>(projects); }
        if (seen != null) { loggedScan = true; seen.append(" -> ").append(out.size()).append(" project(s)"); Vca.MOD.log(seen.toString()); }
        return out;
    }

    private static hSh volume(xJo track) {
        try {
            if (track instanceof com.bitwig.flt.document.core.iface.RYd ch && ch.MIO() != null && ch.MIO().UNj() instanceof hSh h) return h;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void scan() {
        Map<xJo, Link> next = new IdentityHashMap<>();
        for (g2L project : openProjects()) {
            List<xJo> tracks;
            try { tracks = allTracks(project); } catch (Throwable t) { continue; }
            Map<UUID, xJo> byId = new java.util.HashMap<>();
            for (xJo t : tracks) { try { UUID id = t.kFU(); if (id != null) byId.put(id, t); } catch (Throwable ignored) {} }
            for (xJo t : tracks) {
                List<UUID> ids = Vca.members(t);
                if (ids == null) continue;
                hSh vol = volume(t);
                if (vol == null) continue;
                Link l = links.get(t);
                if (l == null) l = new Link(t, vol);
                Map<xJo, Member> keep = new IdentityHashMap<>();
                for (UUID id : ids) {
                    xJo m = byId.get(id);
                    if (m == null || m == t) continue;          // deleted, or the VCA itself
                    Member old = l.members.get(m);
                    if (old != null) { keep.put(m, old); continue; }
                    hSh mv = volume(m);
                    if (mv != null) keep.put(m, new Member(m, mv));
                }
                l.members.clear();
                l.members.putAll(keep);
                next.put(t, l);
            }
        }
        if (next.size() != links.size()) Vca.MOD.log("VCA: " + next.size() + " VCA(s) linked");
        links.clear();
        links.putAll(next);
    }

    // ---- following ----

    static double db(double value) { return value > 0 ? Math.max(FLOOR_DB, 60 * Math.log10(value)) : FLOOR_DB; }
    static double value(double db) { return db <= FLOOR_DB + 1 ? 0 : Math.pow(10, db / 60); }

    private static void follow(Link l) {
        double vDb;
        try { vDb = db(l.vol.iCS()); } catch (Throwable t) { return; }
        for (Member m : l.members.values()) {
            double cur;
            try { cur = m.vol.iCS(); } catch (Throwable t) { continue; }
            // first sight, or moved by hand (or by its own automation): that is its level from now on
            if (Double.isNaN(m.virtualDb) || (!Double.isNaN(m.lastSet) && Math.abs(cur - m.lastSet) > 1e-7)) { m.virtualDb = db(cur); m.lastSet = cur; }
        }
        if (Double.isNaN(l.lastDb)) { l.lastDb = vDb; return; }
        double delta = vDb - l.lastDb;
        l.lastDb = vDb;
        if (Math.abs(delta) < 1e-9) return;
        for (Member m : l.members.values()) {
            m.virtualDb += delta;
            double max;
            try { max = m.vol.QNk().qoU(); } catch (Throwable t) { max = 1.2589; } // +6 dB
            double v = Math.max(0, Math.min(max, value(m.virtualDb)));
            try { m.vol.d_(v); }                                                  // raw: no undo step of its own
            catch (Throwable t) {
                try { m.vol.cfM(v, "VCA", null); } catch (Throwable t2) { Vca.MOD.log("VCA: can't move " + m.track.ca_(), t2); }
            }
            m.lastSet = v;
        }
    }
}
