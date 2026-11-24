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
 * Short audio events (a hit, a slice) move as whole events; long ones (consolidated audio, loops) stay put and their
 * audio follows the groove through their warp markers. The project is not changed.
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
        rates.clear();
        try {
            PSnapAccess sn = new PSnapAccess();
            java.util.Map<Object, Object> sources = Hooks.audioSources.get();
            boolean ownEvents = Overrides.any() && !sources.isEmpty();
            if ((sn.groove == null && !ownEvents) || raw == null || raw.isEmpty() || !init()) { sources.clear(); return; }
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
                if (gr.start == null || gr.end == null) continue; // a hit without its end: left as it is
                // the audio event's own groove (Inspector), else its clip's / track's (up its parents), else the global one
                Groove g = sn.groove; Groove.Amounts a = sn.amounts;
                if (ownEvents) {
                    Object source = sources.get(gr.start);
                    if (source == null && missLogs++ < 3) State.MOD.log("audio event not found for hit at " + time(gr.start));
                    Overrides.Res r = source == null ? null : Overrides.resolve(source);
                    if (r == null && source != null && chainLogs++ < 3) State.MOD.log("audio hit without a groove of its own: " + chain(source));
                    if (r != null) { g = r.groove; a = r.amounts; ownHits++; ownName = g == null ? "Off" : g.name; }
                }
                if (g == null) continue; // straight
                remember(before, gr);
                rewrite(list, gr.start.FzB(), gr, g, a);
            }
            sources.clear();
            int ownCount = ownHits; ownHits = 0;
            if (sumLogs < 30) {
                int longs = 0; for (Group gr : hits) if (gr.start != null && gr.end != null && time(gr.end) - time(gr.start) > 4) longs++;
                String key = hits.size() + "/" + longs + "/" + before.size() + "/" + (sn.groove == null ? "-" : sn.groove.name) + "/" + ownCount;
                if (!key.equals(lastSum)) {
                    lastSum = key; sumLogs++;
                    State.MOD.log("audio clip packed: " + hits.size() + " hit(s), " + longs + " longer than a bar, " + before.size() / 2
                            + " moved or warped; groove " + (sn.groove == null ? "none (straight)" : sn.groove.name) + " (global)" + (ownCount > 0 ? ", " + ownCount + " hit(s) with their own / clip's / track's: " + ownName : ""));
                }
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
    private static int packs, missLogs;
    private static boolean loggedFail;
    private static int diag, sumLogs, ownHits, chainLogs;
    private static String ownName;

    /** An object and its parents, for the log. */
    private static String chain(Object o) {
        StringBuilder b = new StringBuilder();
        for (int d = 0; d < 10 && o != null; d++) { b.append(d == 0 ? "" : " < ").append(o.getClass().getName()); o = Overrides.parent(o); }
        return b.toString();
    }
    private static String lastSum;

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
        final Groove groove; final Groove.Amounts amounts; final boolean own;
        PSnapAccess() {
            // the global groove. (Audio isn't packed through the hooked per-content packers, so the content being
            // packed isn't known here: each hit finds its clip's / track's groove through its own audio event.)
            own = false;
            State.PSnap s = Hooks.packingSnap(); groove = s.active(); amounts = s.amounts;
        }
    }

    /** Warp marker rates changed by warp(), put back with the times if the packer's check fails. */
    private static final Map<fAu, Double> rates = new java.util.IdentityHashMap<>();

    private static void remember(Map<Object, Double> before, Group gr) throws IllegalAccessException {
        before.put(gr.start, time(gr.start)); before.put(gr.end, time(gr.end));
        for (fAu m : gr.markers) before.put(m, time(m));
        for (m2k s : gr.settings) before.put(s, time(s));
    }

    private static void restore(Map<Object, Double> before) {
        for (Map.Entry<fAu, Double> e : rates.entrySet()) {
            try { rateField.setDouble(e.getKey(), e.getValue()); } catch (IllegalAccessException ignored) {}
        }
        rates.clear();
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


    /**
     * Moves one audio event as a whole, as a note moves: its start, warp markers, end and start settings all by the
     * groove's offset at its start. Nothing is added or taken away, so the packer gets the events it made, in the same
     * order. An event longer than a bar (a whole loop in one event) would need stretching: left as it is for now.
     */
    private static void rewrite(List<Object> list, int id, Group gr, Groove g, Groove.Amounts a) throws Exception {
        if (g == null) return;
        double t0 = time(gr.start), t1 = time(gr.end);
        if (!(t1 >= t0)) return;
        if (t1 - t0 > 4) { warp(gr, g, a, t0, t1); return; }
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

    private static int warpLogs;

    /**
     * A long event (consolidated audio, a loop): it stays where it is, and the audio inside it follows the groove
     * through its warp markers. Each marker inside the event moves to where the groove puts its beat, keeping the
     * audio it points at, and the speed between markers is worked out again, so the audio stretches smoothly from hit
     * to hit. Markers are never added (Bitwig's encoder crashes on markers it didn't make): audio without markers at
     * its hits can't follow. Each marker's speed must match the slope to the next one, or the event is left as it is.
     */
    private static void warp(Group gr, Groove g, Groove.Amounts a, double t0, double t1) throws Exception {
        List<fAu> ms = new ArrayList<>(gr.markers);
        ms.sort((x, y) -> { try { return Double.compare(time(x), time(y)); } catch (IllegalAccessException e) { return 0; } });
        int n = ms.size();
        double[] t = new double[n], sec = new double[n], rate = new double[n], nt = new double[n];
        for (int i = 0; i < n; i++) { t[i] = time(ms.get(i)); sec[i] = secondsField.getDouble(ms.get(i)); rate[i] = rateField.getDouble(ms.get(i)); }
        StringBuilder log = warpLogs < 4 ? new StringBuilder("audio groove: long event " + t0 + ".." + t1 + ", " + n + " marker(s):") : null;
        if (log != null) for (int i = 0; i < n && i < 40; i++) log.append(" [").append(t[i]).append("b ").append(sec[i]).append("s ").append(rate[i]).append("s/b]");
        // the markers' speed must be the slope to the next marker (what moving them relies on)
        for (int i = 0; i + 1 < n; i++) {
            double dt = t[i + 1] - t[i];
            if (dt < 1e-9) continue;
            double slope = (sec[i + 1] - sec[i]) / dt;
            if (Math.abs(slope - rate[i]) > 1e-4 * Math.max(1, Math.abs(rate[i]))) {
                if (log != null) { warpLogs++; State.MOD.log(log + "\n  speeds don't follow the markers: left as it is"); }
                return;
            }
        }
        int inside = 0;
        for (int i = 0; i < n; i++) {
            nt[i] = t[i];
            if (t[i] > t0 + 1e-6 && t[i] < t1 - 1e-6) {
                // markers at the same beat move together (same time in, same time out)
                nt[i] = i > 0 && Math.abs(t[i] - t[i - 1]) < 1e-9 ? nt[i - 1] : g.time(t[i], a, Math.round(t[i] * 960) * 0x9E3779B97F4A7C15L);
                inside++;
            }
        }
        if (TEST) for (int i = 0; i < n; i++) if (t[i] > t0 + 1e-6 && t[i] < t1 - 1e-6) nt[i] = t[i] + 0.1;
        // still in order, inside the event, with room between hits
        for (int i = 0; i + 1 < n; i++) {
            boolean same = t[i + 1] - t[i] < 1e-9;
            if (same ? Math.abs(nt[i + 1] - nt[i]) > 1e-9 : nt[i + 1] - nt[i] < 1e-4) {
                if (log != null) { warpLogs++; State.MOD.log(log + "\n  the groove would cross markers: left as it is"); }
                return;
            }
        }
        if (inside == 0) {
            if (log != null) { warpLogs++; State.MOD.log(log + "\n  no warp markers inside: add stretch markers at its hits for the groove to move them"); }
            return;
        }
        for (int i = 0; i < n; i++) {
            fAu m = ms.get(i);
            int j = i + 1; while (j < n && t[j] - t[i] < 1e-9) j++;
            double r = j < n ? (sec[j] - sec[i]) / (nt[j] - nt[i]) : rate[i];
            if (Math.abs(nt[i] - t[i]) > 1e-12 || Math.abs(r - rate[i]) > 1e-12) {
                if (!rates.containsKey(m)) rates.put(m, rate[i]);
                set(m, nt[i]);
                rateField.setDouble(m, r);
            }
        }
        if (log != null) { warpLogs++; StringBuilder b = new StringBuilder(log).append("\n  moved to:"); for (int i = 0; i < n && i < 40; i++) b.append(' ').append(nt[i]); State.MOD.log(b.toString()); }
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
