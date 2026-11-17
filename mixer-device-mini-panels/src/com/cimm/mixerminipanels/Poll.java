package com.cimm.mixerminipanels;

import com.bitwig.flt.app.CHR; // Bitwig's application: asyncExec runs on the UI thread
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** About 30 times a second, on Bitwig's UI thread, repaints shown mini views whose parameters changed. */
final class Poll {
    private static final Set<MiniGraph> shown = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static ScheduledExecutorService timer;
    private static volatile boolean queued;
    private static boolean logged;

    private Poll() {}

    static synchronized void add(MiniGraph g) { shown.add(g); start(); }

    private static synchronized void start() {
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cimm-mini-view-poll"); t.setDaemon(true); return t; });
            timer.scheduleAtFixedRate(Poll::tick, 16, 16, TimeUnit.MILLISECONDS); // ~60 per second
        }
    }

    static void remove(MiniGraph g) { shown.remove(g); }

    private static void tick() {
        if (queued || shown.isEmpty()) return;
        queued = true;
        try {
            CHR.Grk().asyncExec(() -> { // on the UI thread
                queued = false;
                MiniGraph[] gs;
                synchronized (shown) { gs = shown.toArray(new MiniGraph[0]); }
                if (!logged) { logged = true; MiniGraph.MOD.log("poll running: " + gs.length + " views"); }
                for (MiniGraph g : gs) { try { g.pollRepaint(); } catch (Throwable ignored) {} }
            });
        } catch (Throwable t) {
            queued = false;
        }
    }
}
