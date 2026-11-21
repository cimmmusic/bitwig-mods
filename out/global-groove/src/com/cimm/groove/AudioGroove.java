package com.cimm.groove;

import bitwig.obf.ALv; // an audio event's end
import bitwig.obf.ERu; // one event of a clip's engine bytes (time + id)
import bitwig.obf.ZKD; // an audio event's start
import bitwig.obf.fAu; // an audio warp marker: at this beat, the audio is at "seconds", going at "seconds per beat"
import bitwig.obf.m2k; // an audio event setting placed at its start

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Audio clips follow the groove: each (stretched) audio event's warp markers are rewritten on the way to the engine,
 * so the audio that was at each Base step plays where the groove puts that step, stretching smoothly in between.
 * Audio without warp markers isn't stretched by the engine and stays as it is. The project is not changed.
 */
final class AudioGroove {
    private AudioGroove() {}

    /** Off: Bitwig's encoder looks up each warp marker it made, so added markers crash it (being worked on). */
    static final boolean ENABLED = true;
    /** Dry run: plan and log only, change nothing. */
    static final boolean DRY = false;
    private static int dryLogs;

    private static Field timeField, secondsField, rateField;
    private static boolean ready, failed, logged;

    private static boolean init() {
        if (ready || failed) return ready;
        try {
            for (Field f : ERu.class.getDeclaredFields()) // the time: ERu's double that isn't final (it has a setter)
                if (f.getType() == double.class && !java.lang.reflect.Modifier.isFinal(f.getModifiers()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) timeField = f;
            List<Field> d = new ArrayList<>(); // fAu's two final doubles, in declaration order: seconds, then seconds per beat
            for (Field f : fAu.class.getDeclaredFields())
                if (f.getType() == double.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) d.add(f);
            if (timeField == null || d.size() != 2) throw new IllegalStateException("event fields not as expected");
            secondsField = d.get(0); rateField = d.get(1);
            timeField.setAccessible(true); secondsField.setAccessible(true); rateField.setAccessible(true);
            ready = true;
        } catch (Throwable t) { failed = true; State.MOD.log("audio groove unavailable", t); }
        return ready;
    }

    static double time(ERu e) throws IllegalAccessException { return timeField.getDouble(e); }
    /** Sets an event's time (Bitwig's own cfM(double) subtracts from it: not a setter). */
    static void set(ERu e, double t) throws IllegalAccessException { timeField.setDouble(e, t); }

    private static final class Group { ZKD start; ALv end; final List<fAu> markers = new ArrayList<>(); final List<m2k> settings = new ArrayList<>(); }

    /**
     * Rewrites the audio events of one clip's event list (Bitwig sorts it right after). Each audio hit (start, its
     * markers and settings, end: in time order per id, as ids are reused from hit to hit) moves as a whole. Before
     * handing the list back, the packer's own check is simulated (every end after a start of its id); if it fails,
     * every time is put back as it was, and the order is logged once: playing straight, never crashing.
     */
    @SuppressWarnings("unchecked")
    static void apply(List<?> raw) {
        Map<Object, Double> before = new java.util.IdentityHashMap<>();
        try {
            PSnapAccess sn = new PSnapAccess();
            if (sn.groove == null || raw == null || raw.isEmpty() || !init()) return;
            List<Object> list = (List<Object>) raw;
            List<Object> sorted = new ArrayList<>(list);
            java.util.Collections.sort((List) sorted);
            // hits: per id, a start opens one, its markers/settings join it, an end closes it
            Map<Integer, Group> open = new HashMap<>();
            List<Group> hits = new ArrayList<>();
            for (Object o : sorted) {
                if (o instanceof ZKD z) { Group gr = new Group(); gr.start = z; open.put(z.FzB(), gr); hits.add(gr); }
                else if (o instanceof fAu m) { Group gr = open.get(m.FzB()); if (gr != null) gr.markers.add(m); }
                else if (o instanceof m2k st) { Group gr = open.get(st.FzB()); if (gr != null) gr.settings.add(st); }
                else if (o instanceof ALv e) { Group gr = open.remove(e.FzB()); if (gr != null) gr.end = e; }
            }
            for (Group gr : hits) {
                if (gr.start == null || gr.end == null || gr.markers.isEmpty()) continue; // not stretched: left as it is
                remember(before, gr);
                rewrite(list, gr.start.FzB(), gr, sn.groove, sn.amounts);
            }
            if (diag < 2 && !hits.isEmpty()) {
                diag++;
                StringBuilder b = new StringBuilder("audio diag: via " + caller() + ", " + hits.size() + " hit(s), " + before.size() + " event(s) moved");
                int k = 0;
                for (Group gr : hits) {
                    if (k++ >= 6) break;
                    b.append("\n  hit id=").append(gr.start.FzB()).append(" start=").append(time(gr.start))
                     .append(" end=").append(gr.end == null ? "-" : String.valueOf(time(gr.end)))
                     .append(" markers=").append(gr.markers.size()).append(" zkd=").append(fields(gr.start));
                    for (fAu m : gr.markers) b.append(" [m ").append(time(m)).append("b ").append(secondsField.getDouble(m)).append("s]");
                }
                State.MOD.log(b.toString());
            }
            if (packs++ < 40) State.MOD.log("audio pack " + packs + ": " + hits.size() + " hit(s), list " + System.identityHashCode(list));
            if (!before.isEmpty() && !packable(list)) {
                restore(before);
                if (!loggedFail) { loggedFail = true; State.MOD.log("audio groove: would upset Bitwig's packer, left straight:" + order(list)); }
            }
        } catch (Throwable t) {
            try { restore(before); } catch (Throwable ignored) {}
            State.MOD.log("audio groove failed (left straight)", t);
        }
    }

    /** Test build: audio hits all half a beat late, to hear whether moving them reaches playback at all. */
    static final boolean TEST = false;
    private static int packs;
    private static boolean loggedFail;
    private static int diag;

    private static String caller() {
        StringBuilder b = new StringBuilder();
        StackTraceElement[] st = new Throwable().getStackTrace();
        for (int i = 2; i < Math.min(st.length, 9); i++) b.append(st[i].getClassName()).append('.').append(st[i].getMethodName()).append(' ');
        return b.toString();
    }

    private static String fields(Object o) {
        StringBuilder b = new StringBuilder("{");
        try {
            for (Field f : o.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                b.append(f.getName()).append('=').append(f.get(o)).append(' ');
            }
        } catch (Throwable ignored) {}
        return b.append('}').toString();
    }

    /** The groove for the clip being packed. */
    private static final class PSnapAccess {
        final Groove groove; final Groove.Amounts amounts;
        PSnapAccess() { State.PSnap s = Hooks.packingSnap(); groove = s.active(); amounts = s.amounts; }
    }

    private static void remember(Map<Object, Double> before, Group gr) throws IllegalAccessException {
        before.put(gr.start, time(gr.start)); before.put(gr.end, time(gr.end));
        for (fAu m : gr.markers) before.put(m, time(m));
        for (m2k s : gr.settings) before.put(s, time(s));
    }

    private static void restore(Map<Object, Double> before) {
        for (Map.Entry<Object, Double> e : before.entrySet()) {
            try { set((ERu) e.getKey(), e.getValue()); } catch (IllegalAccessException ignored) {}
        }
    }

    /** The packer's first pass: sorted, every audio end has had a start of its id. */
    @SuppressWarnings("unchecked")
    private static boolean packable(List<Object> list) {
        List<Object> sorted = new ArrayList<>(list);
        java.util.Collections.sort((List) sorted);
        java.util.Set<Integer> started = new java.util.HashSet<>();
        for (Object o : sorted) {
            if (o instanceof ZKD z) started.add(z.FzB());
            else if (o instanceof ALv e && !started.contains(e.FzB())) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static String order(List<Object> list) {
        List<Object> sorted = new ArrayList<>(list);
        java.util.Collections.sort((List) sorted);
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Object o : sorted) {
            if (n++ > 60) { b.append("\n  ..."); break; }
            try { b.append("\n  ").append(o.getClass().getSimpleName()).append(" t=").append(time((ERu) o)).append(" id=").append(((ERu) o).FzB()); }
            catch (Throwable ignored) {}
        }
        return b.toString();
    }

    private static boolean loggedLong;

    /**
     * Moves one audio event as a whole, as a note moves: its start, warp markers, end and start settings all by the
     * groove's offset at its start. Nothing is added or taken away, so the packer gets the events it made, in the same
     * order. An event longer than a bar (a whole loop in one event) would need stretching: left as it is for now.
     */
    private static void rewrite(List<Object> list, int id, Group gr, Groove g, Groove.Amounts a) throws Exception {
        if (g == null) return;
        double t0 = time(gr.start), t1 = time(gr.end);
        if (!(t1 >= t0)) return;
        if (t1 - t0 > 4) {
            if (!loggedLong) { loggedLong = true; State.MOD.log("audio groove: an event longer than a bar is left as it is (" + t0 + ".." + t1 + ")"); }
            return;
        }
        double shift = g.time(t0, a, Math.round(t0 * 960) * 0x9E3779B97F4A7C15L) - t0;
        if (TEST) shift = 0.5; // test: every audio hit half a beat late, unmistakable
        if (t0 >= 0 && t0 + shift < 0) shift = -t0; // nothing before the clip's start
        if (Math.abs(shift) < 1e-9) return;
        set(gr.start, t0 + shift);
        set(gr.end, t1 + shift);
        for (fAu m : gr.markers) set(m, time(m) + shift);
        for (m2k s : gr.settings) set(s, time(s) + shift);
        if (!logged) { logged = true; State.MOD.log("audio groove: event " + id + " " + t0 + ".." + t1 + " moved by " + shift); }
    }

    private static int index(double t, double[] mt) {
        int i = 0;
        while (i + 1 < mt.length && mt[i + 1] <= t) i++;
        return i;
    }

    /** Where the audio is (seconds) at beat t, by the original markers (before the first: from it, at its rate). */
    private static double seconds(double t, double[] mt, double[] ms, double[] mr) {
        int i = index(t, mt);
        return ms[i] + (t - mt[i]) * mr[i];
    }
}
