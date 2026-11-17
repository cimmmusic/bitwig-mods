package com.cimm.mixerminipanels;

import bitwig.obf.hSh; // a settable device parameter
import bml.api.Bml;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Live modulation of device parameters (an LFO on Filter+'s cutoff, …).
 *
 * Bitwig's parameter objects (XdY, implemented by Opy) hold three plain values with their own observables: iCS() — the
 * knob's value, which the views use — and hUR() / SQN(). One of the latter two is the modulated value Bitwig's knobs
 * draw as their ring; which one isn't known from the obfuscated code, so it's found live: the candidate that moves
 * while the knob itself stays put. The first sighting per candidate is logged ("modulation: …") to confirm it.
 */
final class Modulation {
    private static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    private Modulation() {}

    static final long HOLD_MS = 1500; // a candidate counts as modulating for this long after it last moved

    private static final class Track {
        double v = Double.NaN, h = Double.NaN, s = Double.NaN;
        long hAt, sAt;
    }
    private static final Map<hSh, Track> tracks = new WeakHashMap<>();
    private static int logged;
    private static final java.util.Set<String> loggedWhich = new java.util.HashSet<>();

    /** Reads the three values and notes which candidate moved on its own. Call regularly (UI thread). */
    static void track(Params.P p) {
        if (p == null) return;
        hSh x = p.param();
        double v, h, s;
        try { v = x.iCS(); h = x.hUR(); s = x.SQN(); } catch (Throwable t) { return; }
        Track t; synchronized (tracks) { t = tracks.computeIfAbsent(x, k -> new Track()); }
        long now = System.currentTimeMillis();
        double eps = Math.max(1e-9, Math.abs(p.max() - p.min()) * 1e-6);
        boolean knobMoved = !Double.isNaN(t.v) && Math.abs(v - t.v) > eps;
        if (!knobMoved && !Double.isNaN(t.h) && Math.abs(h - t.h) > eps) { t.hAt = now; note(p, "hUR", v, h, s); }
        if (!knobMoved && !Double.isNaN(t.s) && Math.abs(s - t.s) > eps) { t.sAt = now; note(p, "SQN", v, h, s); }
        t.v = v; t.h = h; t.s = s;
    }

    private static void note(Params.P p, String which, double v, double h, double s) {
        if (logged < 40 && loggedWhich.add(p.id() + which)) {
            logged++;
            MOD.log("modulation: " + p.id() + " '" + p.name() + "' moves via " + which + "  knob(iCS)=" + v + " hUR=" + h + " SQN=" + s
                    + " range [" + p.min() + ", " + p.max() + "]");
        }
    }

    /** The modulated plain value, or the knob's value when nothing modulates it. */
    static double value(hSh x) {
        Track t;
        synchronized (tracks) { t = tracks.get(x); }
        double v;
        try { v = x.iCS(); } catch (Throwable e) { return 0; }
        if (t == null) return v;
        long now = System.currentTimeMillis();
        boolean h = now - t.hAt < HOLD_MS, s = now - t.sAt < HOLD_MS;
        try {
            if (s && (!h || t.sAt >= t.hAt)) return x.SQN();
            if (h) return x.hUR();
        } catch (Throwable ignored) {}
        return v;
    }

    /** Is the parameter being modulated right now? */
    static boolean active(Params.P p) {
        if (p == null) return false;
        Track t;
        synchronized (tracks) { t = tracks.get(p.param()); }
        if (t == null) return false;
        long now = System.currentTimeMillis();
        return now - t.hAt < HOLD_MS || now - t.sAt < HOLD_MS;
    }
}
