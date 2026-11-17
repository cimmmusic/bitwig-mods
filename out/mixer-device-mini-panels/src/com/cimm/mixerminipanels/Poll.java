package com.cimm.mixerminipanels;

import com.bitwig.flt.app.CHR; // Bitwig's application: asyncExec runs on the UI thread
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * About 60 times a second, on Bitwig's UI thread, lets the mini views ON SCREEN check their parameters (and repaint when
 * they changed). Views join when Bitwig attaches them and leave when it detaches them, so rows the mixer rebuilt or
 * scrolled away cost nothing; the set is weak as a safety net. When no view is shown the timer does nothing.
 */
final class Poll {
    private static final Set<MiniGraph> shown = Collections.newSetFromMap(new WeakHashMap<>());
    /** A copy of {@link #shown} for the timer and the scroll hook, rebuilt on every change (no copying per tick). */
    private static volatile MiniGraph[] snapshot = new MiniGraph[0];
    private static ScheduledExecutorService timer;
    private static volatile boolean queued;

    private Poll() {}

    static synchronized void add(MiniGraph g) { if (shown.add(g)) snapshot = shown.toArray(new MiniGraph[0]); start(); }
    static synchronized void remove(MiniGraph g) { if (shown.remove(g)) snapshot = shown.toArray(new MiniGraph[0]); }

    /** The views on screen now. */
    static MiniGraph[] views() { return snapshot; }

    private static synchronized void start() {
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cimm-mini-view-poll"); t.setDaemon(true); return t; });
            timer.scheduleAtFixedRate(Poll::tick, 16, 16, TimeUnit.MILLISECONDS); // ~60 per second
        }
    }

    private static void tick() {
        if (queued || snapshot.length == 0) return;
        queued = true;
        try {
            CHR.Grk().asyncExec(() -> { // on the UI thread
                queued = false;
                for (MiniGraph g : snapshot) { try { g.pollRepaint(); } catch (Throwable ignored) {} }
            });
        } catch (Throwable t) {
            queued = false;
        }
    }
}
