package com.cimm.mixerpregain;

import com.bitwig.flt.app.CHR; // Bitwig's application: asyncExec runs on the UI thread
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** About 30 times a second, on Bitwig's UI thread: each knob keeps its track's pre gain in place and repaints on change. */
final class Poll {
    private static final Set<GainKnob> knobs = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static ScheduledExecutorService timer;
    private static volatile boolean queued;
    private Poll() {}

    static synchronized void addKnob(GainKnob k) {
        knobs.add(k);
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cimm-pre-gain-poll"); t.setDaemon(true); return t; });
            timer.scheduleAtFixedRate(Poll::tick, 33, 33, TimeUnit.MILLISECONDS);
        }
    }

    private static void tick() {
        if (queued || knobs.isEmpty()) return;
        queued = true;
        try {
            CHR.Grk().asyncExec(() -> {
                queued = false;
                GainKnob[] ks;
                synchronized (knobs) { ks = knobs.toArray(new GainKnob[0]); }
                for (GainKnob k : ks) { try { k.tick(); } catch (Throwable ignored) {} }
            });
        } catch (Throwable t) { queued = false; }
    }
}
