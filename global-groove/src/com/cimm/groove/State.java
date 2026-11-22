package com.cimm.groove;

import bml.api.Bml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the GROOVE section shows and what playback uses: the source (Bitwig's own shuffle/accent, a groove file, or a
 * groove drawn by hand in the Manual graph), the loaded grooves, the selected one, the manual steps and each source's
 * amounts. Remembered in the mod's settings.
 *
 * Playback applies a groove only when the source is Groove File or Manual and Bitwig's Enable Groove is on.
 */
final class State {
    private State() {}

    static final Bml.Mod MOD = Bml.mod("global-groove");

    static final class Entry {
        final Path path; final Groove groove;
        Entry(Path path, Groove groove) { this.path = path; this.groove = groove; }
    }

    static final int BITWIG = 0, FILE = 1, MANUAL = 2;
    static final String[] SOURCES = {"bitwig", "file", "manual"};
    static volatile int mode = BITWIG;
    /** A groove of ours (file or manual) is in use, so Bitwig's own shuffle/accent is paused. */
    static boolean usesGroove() { return mode != BITWIG; }

    /** Bitwig's own Shuffle / Accent from before a groove of ours set them to 0 (NaN: nothing kept). Per project. */
    static double savedShuffle = Double.NaN, savedAccent = Double.NaN;
    /** Bitwig's own Enable Groove, read from the project (off until it has been read, so nothing is grooved by
     *  mistake while a project opens). */
    static volatile boolean grooveOn = false;
    static final List<Entry> grooves = new ArrayList<>();
    static volatile int current = -1;
    /** Each source keeps its own amounts (switching never overwrites the other's). */
    static volatile Groove.Amounts fileAmounts = fileDefaults(), manualAmounts = manualDefaults();
    static Groove.Amounts fileDefaults() { Groove.Amounts a = new Groove.Amounts(); a.timing = 1; a.velocity = 0; a.base = 3; return a; }
    static Groove.Amounts amounts() { return mode == MANUAL ? manualAmounts : fileAmounts; }

    static Groove.Amounts manualDefaults() { Groove.Amounts a = new Groove.Amounts(); a.timing = 1; a.velocity = 1; a.base = 3; return a; }

    // ---- the Manual graph: one bar of steps at the manual Base, each with a timing offset (fraction of a step,
    //      -0.5..0.5) and a velocity (1..127); kept per Base so switching Base and back keeps the drawing ----
    static final double BAR = 4;
    private static final java.util.Map<Integer, double[][]> manual = new java.util.HashMap<>();
    private static volatile Groove manualGroove;

    static int manualSteps(int base) { return Math.max(1, (int) Math.round(BAR / Groove.BASES[base])); }

    /** Manual slots A..D: four drawings. The Play menu and Groove panel edit 'slot'; the global Manual source plays
     *  'playSlot' (its own choice, so B..D can be drawn for notes / clips while the global groove stays on A); notes,
     *  clips, tracks and audio events each use the slot they're set to (Overrides). */
    static final String[] SLOT_NAMES = {"A", "B", "C", "D"};
    static volatile int slot = 0, playSlot = 0;
    static int key(int slot, int base) { return slot * 16 + base; }

    /** {offsets, velocities} for a Base in the selected slot (created straight: no offsets, velocity 100). */
    static double[][] manual(int base) { return manual(slot, base); }

    static synchronized double[][] manual(int slot, int base) { return manualKey(key(slot, base)); }

    /** By storage key (slot * 16 + base). */
    static synchronized double[][] manualKey(int key) {
        return manual.computeIfAbsent(key, k -> {
            int n = manualSteps(k % 16);
            double[] v = new double[n];
            java.util.Arrays.fill(v, 100);
            return new double[][]{new double[n], v};
        });
    }

    static Groove manualGroove() {
        Groove g = manualGroove;
        if (g == null || g.base != manualAmounts.base) {
            g = manualGroove(playSlot, manualAmounts.base);
            manualGroove = g;
        }
        return g;
    }

    /** Built groove per (slot, base), dropped whenever a drawing changes. */
    private static final java.util.Map<Integer, Groove> built = new java.util.concurrent.ConcurrentHashMap<>();

    /** A slot's groove at a Base. */
    static Groove manualGroove(int slot, int base) {
        int k = key(slot, base);
        Groove cached = built.get(k);
        if (cached != null) return cached;
        Groove g;
        {
            double[][] m = manual(slot, base);
            double b = Groove.BASES[base];
            g = new Groove();
            g.name = "Manual " + SLOT_NAMES[slot];
            g.length = BAR;
            g.base = base;
            g.times = new double[m[0].length];
            g.velocities = new double[m[0].length];
            for (int i = 0; i < g.times.length; i++) { g.times[i] = (i + m[0][i]) * b; g.velocities[i] = m[1][i]; }
            g.fixedOffsets = new double[m[0].length];
            g.fixedVelocities = m[1].clone();
            for (int i = 0; i < m[0].length; i++) g.fixedOffsets[i] = m[0][i] * b;
        }
        built.put(k, g);
        return g;
    }

    /** The global Manual source plays another slot. */
    static void setPlaySlot(int s) {
        if (s == playSlot || s < 0 || s >= SLOT_NAMES.length) return;
        willChange(null);
        playSlot = s;
        manualGroove = null;
        changed(true);
    }

    /** The Play menu / Groove panel chose another slot to edit (the global Manual source keeps its own). */
    static void setSlot(int s) {
        if (s == slot || s < 0 || s >= SLOT_NAMES.length) return;
        willChange(null);
        slot = s;
        manualGroove = null; built.clear();
        changed(true);
    }

    // ---- undo / redo of every groove change: the whole state before each change, with when it was made. Bitwig's own
    //      Undo / Redo (Cmd+Z / Cmd+Shift+Z) and the Undo / Redo buttons step through it, in time order with Bitwig's
    //      own history (Hooks). ----
    static final class Snap {
        final long time; final int mode, current, slot, playSlot; final Groove.Amounts file, man; final java.util.Map<Integer, double[][]> steps;
        Snap(long time) {
            this.time = time; mode = State.mode; current = State.current; slot = State.slot; playSlot = State.playSlot;
            file = copy(fileAmounts); man = copy(manualAmounts);
            steps = new java.util.HashMap<>();
            synchronized (State.class) { for (var e : manual.entrySet()) steps.put(e.getKey(), new double[][]{e.getValue()[0].clone(), e.getValue()[1].clone()}); }
        }
    }
    private static final java.util.ArrayDeque<Snap> undo = new java.util.ArrayDeque<>(), redo = new java.util.ArrayDeque<>();
    private static String lastKey; private static long lastKeyAt;

    static Groove.Amounts copy(Groove.Amounts a) {
        Groove.Amounts c = new Groove.Amounts();
        c.strength = a.strength; c.timing = a.timing; c.velocity = a.velocity; c.random = a.random; c.quantize = a.quantize; c.base = a.base;
        return c;
    }

    /**
     * Call before changing anything. Changes with the same key less than a second apart are one step (a knob drag);
     * a null key is always a step of its own.
     */
    static void willChange(String key) {
        long now = System.currentTimeMillis();
        boolean merge = key != null && key.equals(lastKey) && now - lastKeyAt < 1000;
        lastKey = key; lastKeyAt = now;
        if (merge) return;
        undo.push(new Snap(now));
        while (undo.size() > 200) undo.removeLast();
        redo.clear();
    }

    static void manualWillChange() { willChange(null); }

    static long undoTime() { Snap s = undo.peek(); return s == null ? Long.MIN_VALUE : s.time; }
    static long redoTime() { Snap s = redo.peek(); return s == null ? Long.MAX_VALUE : s.time; }

    private static void restore(Snap s) {
        mode = s.mode; slot = s.slot; playSlot = s.playSlot;
        synchronized (grooves) { current = s.current < grooves.size() ? s.current : current; }
        fileAmounts = copy(s.file); manualAmounts = copy(s.man);
        synchronized (State.class) {
            for (var e : s.steps.entrySet()) {
                double[][] m = manualKey(e.getKey());
                System.arraycopy(e.getValue()[0], 0, m[0], 0, Math.min(m[0].length, e.getValue()[0].length));
                System.arraycopy(e.getValue()[1], 0, m[1], 0, Math.min(m[1].length, e.getValue()[1].length));
            }
        }
        lastKey = null;
        manualGroove = null; built.clear();
        changed(true);
        Hooks.resendGrooveSwitch();
    }

    static boolean groovUndo() {
        Snap s = undo.poll();
        if (s == null) return false;
        Snap now = new Snap(s.time); // redo returns to this, and keeps the change's place in time
        redo.push(now);
        restore(s);
        MOD.log("undo groove change");
        return true;
    }

    static boolean groovRedo() {
        Snap s = redo.poll();
        if (s == null) return false;
        undo.push(new Snap(s.time));
        restore(s);
        MOD.log("redo groove change");
        return true;
    }

    /** The Undo / Redo buttons: the groove history only. */
    static void manualUndo() { ProjectStore.undo(); }
    static void manualRedo() { ProjectStore.redo(); }

    /** A step was dragged: replay with it. */
    static void manualChanged() { manualGroove = null; built.clear(); changed(true); }

    static void manualReset() {
        manualWillChange();
        double[][] m = manual(manualAmounts.base);
        java.util.Arrays.fill(m[0], 0);
        java.util.Arrays.fill(m[1], 100);
        manualChanged();
    }

    /** Writes the Manual groove to an .agr file and adds it to the groove file list (selected, but the source stays
     *  Manual). UI thread. */
    static void saveManual(Path file) {
        try {
            Groove g = manualGroove(slot, manualAmounts.base); // the drawing being edited
            Groove named = new Groove();
            named.name = file.getFileName().toString().replaceFirst("(?i)\\.agr$", "");
            named.length = g.length; named.base = g.base; named.times = g.times; named.velocities = g.velocities;
            Files.createDirectories(file.getParent());
            Files.writeString(file, named.toAgr(manualAmounts), java.nio.charset.StandardCharsets.UTF_8);
            MOD.log("manual groove saved: " + file);
            int keep = mode;
            addFiles(java.util.List.of(file));
            if (mode != keep) setMode(keep);
            message = null;
            repaint();
        } catch (Throwable t) {
            message = "Couldn't save the groove: " + t.getMessage();
            MOD.log("saving manual groove failed", t);
            repaint();
        }
    }

    /** Starts the graph from the selected groove file: each step takes that groove's offset and velocity there. */
    static void manualFromFile() {
        Entry e = selected();
        if (e == null) { message = "No groove file loaded to copy"; repaint(); return; }
        int base = manualAmounts.base;
        manualWillChange();
        double[][] m = manual(base);
        double[][] from = e.groove.steps(base, m[0].length);
        System.arraycopy(from[0], 0, m[0], 0, m[0].length);
        System.arraycopy(from[1], 0, m[1], 0, m[1].length);
        manualChanged();
    }
    /** A message shown in place of the groove name (a file that couldn't be read), cleared by the next action. */
    static volatile String message;

    private static boolean loaded;
    private static final List<Runnable> views = new ArrayList<>();

    /** The groove playback should apply now, or null. */
    static Groove active() {
        load();
        if (!grooveOn) return null;
        if (mode == MANUAL) return manualGroove();
        if (mode != FILE) return null;
        Entry e = selected();
        return e == null ? null : e.groove;
    }

    // ---- per project: what playback uses for each open project's clips (the current project's is kept up to date
    //      from these settings; the others keep the snapshot taken when they were last current) ----
    static final class PSnap {
        final int mode; final Groove groove; final Groove.Amounts amounts; volatile boolean on;
        PSnap(int mode, Groove groove, Groove.Amounts amounts, boolean on) { this.mode = mode; this.groove = groove; this.amounts = amounts; this.on = on; }
        boolean usesGroove() { return mode != BITWIG; }
        Groove active() { return on ? groove : null; }
    }
    private static final Map<Object, PSnap> snaps = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** The current project's document (key of its snapshot). */
    static volatile Object currentDoc;

    /** The current settings, as the current project's snapshot. */
    static void publish() {
        Object d = currentDoc;
        if (d == null) return;
        Groove g = mode == MANUAL ? manualGroove() : mode == FILE && selected() != null ? selected().groove : null;
        snaps.put(d, new PSnap(mode, g, copy(amounts()), grooveOn));
    }

    /** The snapshot for a project's document (or one made from the current settings when unknown). */
    static PSnap snap(Object doc) {
        PSnap s = doc == null ? null : snaps.get(doc);
        if (s != null) return s;
        Groove g = !grooveOn ? null : mode == MANUAL ? manualGroove() : mode == FILE && selected() != null ? selected().groove : null;
        return new PSnap(mode, g, amounts(), grooveOn);
    }

    /** A project's Enable Groove changed. */
    static void grooveOnFor(Object doc, boolean on) {
        PSnap s = doc == null ? null : snaps.get(doc);
        if (s != null) s.on = on;
        if (doc != null && doc == currentDoc) grooveOn = on;
    }

    static Entry selected() {
        synchronized (grooves) { return current >= 0 && current < grooves.size() ? grooves.get(current) : null; }
    }

    static List<Entry> list() { synchronized (grooves) { return new ArrayList<>(grooves); } }

    // ---- changes (UI thread) ----

    static void setMode(int m) {
        if (mode == m) return;
        willChange(null);
        mode = m;
        MOD.log("source: " + SOURCES[m]);
        message = null;
        changed(true);
        Hooks.resendGrooveSwitch(); // Bitwig's own groove: muted towards the engine while a groove of ours is used
    }

    /** Adds grooves from files (already loaded ones are just selected); selects the last one that loaded. */
    static void addFiles(List<Path> files) {
        load();
        message = null;
        int pick = -1;
        List<String> failed = new ArrayList<>();
        for (Path p : files) {
            int at = indexOf(p);
            if (at >= 0) { pick = at; continue; }
            try {
                Groove g = Groove.read(p);
                synchronized (grooves) { grooves.add(new Entry(p, g)); pick = grooves.size() - 1; }
                MOD.log("groove loaded: " + g);
            } catch (Throwable t) {
                failed.add(p.getFileName() + ": " + t.getMessage());
                MOD.log("couldn't read groove " + p + ": " + t);
            }
        }
        if (!failed.isEmpty()) message = "Couldn't read " + String.join("; ", failed);
        if (pick >= 0) select(pick, true);
        else changed(false);
    }

    /** Selects a loaded groove; its file's Timing/Velocity/Random/Quantize/Base become the amounts (Strength stays). */
    static void select(int index, boolean fromFile) {
        willChange("groove");
        Entry e;
        synchronized (grooves) { if (index < 0 || index >= grooves.size()) return; current = index; e = grooves.get(index); }
        {
            Groove.Amounts a = Groove.Amounts.from(e.groove);
            a.strength = fileAmounts.strength;
            fileAmounts = a;
        }
        message = null;
        changed(true);
    }

    static void step(int by) {
        int n; synchronized (grooves) { n = grooves.size(); }
        if (n == 0) return;
        select(Math.floorMod((current < 0 ? 0 : current) + by, n), true);
    }

    static void remove(int index) {
        synchronized (grooves) {
            if (index < 0 || index >= grooves.size()) return;
            grooves.remove(index);
            if (current >= grooves.size()) current = grooves.size() - 1;
        }
        changed(true);
    }

    /** An amount changed (knobs, Base): replay with it. */
    static void amountsChanged() { manualGroove = null; built.clear(); changed(true); }

    /** Overrides changed: replay and store. */
    static void overridesChanged() { changed(true); }

    private static void changed(boolean replay) {
        publish();
        save();
        if (replay) Hooks.repackAll();
        repaint();
    }

    // ---- views ----

    static void onChange(Runnable r) { synchronized (views) { views.add(r); } }
    static void clearViews() { synchronized (views) { views.clear(); } }

    static void repaint() {
        Runnable[] rs; synchronized (views) { rs = views.toArray(new Runnable[0]); }
        for (Runnable r : rs) try { r.run(); } catch (Throwable ignored) {}
        try { Graph.refreshPanels(); } catch (Throwable ignored) {}
    }

    // ---- remembered between sessions ----

    private static int indexOf(Path p) {
        synchronized (grooves) {
            for (int i = 0; i < grooves.size(); i++) if (grooves.get(i).path.toAbsolutePath().equals(p.toAbsolutePath())) return i;
        }
        return -1;
    }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            bml.api.Settings s = MOD.settings();
            mode = java.util.Arrays.asList(SOURCES).indexOf(s.get("source", "bitwig"));
            if (mode < 0) mode = BITWIG;
            for (String line : s.get("grooves", "").split("\n")) {
                if (line.isBlank()) continue;
                Path p = Path.of(line);
                if (!Files.exists(p)) { MOD.log("groove file missing: " + p); continue; }
                try { grooves.add(new Entry(p, Groove.read(p))); } catch (Throwable t) { MOD.log("couldn't read groove " + p + ": " + t); }
            }
            String cur = s.get("current", "");
            for (int i = 0; i < grooves.size(); i++) if (grooves.get(i).path.toString().equals(cur)) current = i;
            if (current < 0 && !grooves.isEmpty()) current = 0;
            fileAmounts = readAmounts(s, "", new Groove.Amounts());
            manualAmounts = readAmounts(s, "m_", manualDefaults());
            if (manualAmounts.strength == 0 && manualAmounts.timing == 0) { // only ever switches the groove off: repair
                manualAmounts.strength = 1; manualAmounts.timing = 1;
                MOD.log("Manual Strength and Timing were both 0 %: set back to 100 %");
            }
            for (int b = 0; b < SLOT_NAMES.length * 16; b++) {
                if (b % 16 >= Groove.BASES.length) continue;
                String line = s.get("manual_" + b, "");
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                double[][] m = manualKey(b);
                for (int i = 0; i < Math.min(parts.length, m[0].length); i++) {
                    String[] ov = parts[i].split(":");
                    if (ov.length == 2) { m[0][i] = num(ov[0], 0); m[1][i] = num(ov[1], 100); }
                }
            }
            MOD.log("groove settings: source " + SOURCES[mode] + ", " + grooves.size() + " groove(s), current " + current);
        } catch (Throwable t) { MOD.log("couldn't read groove settings", t); }
    }

    // ---- per project: the groove settings are stored in the project itself (ProjectStore) ----
    private static volatile boolean restoring;

    /** The project's settings (or the defaults when it has none): shown and played, not written back. UI thread. */
    static synchronized void fromProject(String blob) {
        if (blob == null || blob.isEmpty()) resetToDefaults(); else restoreProject(blob);
        manualGroove = null; built.clear();
        undo.clear(); redo.clear();
        MOD.log(blob == null || blob.isEmpty() ? "project: default groove settings" : "project: its groove settings, source " + SOURCES[mode]);
        restoring = true;
        try { changed(true); } finally { restoring = false; }
        Hooks.resendGrooveSwitch();
        // Undo / Redo, opening, switching tab: corners, Inspector rows and clips' Shuffle follow at once
        try { Hooks.repaintMarks(); } catch (Throwable ignored) {}
        try { GrooveInspector.refreshAll(); } catch (Throwable ignored) {}
    }

    static void resetToDefaults() {
        mode = BITWIG;
        slot = 0; playSlot = 0;
        Overrides.clear();
        fileAmounts = fileDefaults();
        manualAmounts = manualDefaults();
        synchronized (State.class) { manual.clear(); }
        message = null;
    }

    static String dumpProject() {
        java.util.Properties p = new java.util.Properties();
        p.setProperty("source", SOURCES[mode]);
        p.setProperty("manual_slot", "" + slot);
        p.setProperty("manual_play_slot", "" + playSlot);
        Overrides.dump(p);
        Entry e = selected();
        if (e != null) p.setProperty("current", e.path.toString());
        putAmounts(p, "", fileAmounts);
        putAmounts(p, "m_", manualAmounts);
        if (!Double.isNaN(savedShuffle)) p.setProperty("bitwig_shuffle", "" + savedShuffle);
        if (!Double.isNaN(savedAccent)) p.setProperty("bitwig_accent", "" + savedAccent);
        synchronized (State.class) {
            for (var m : manual.entrySet()) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < m.getValue()[0].length; i++)
                    line.append(i == 0 ? "" : ",").append(m.getValue()[0][i]).append(':').append(m.getValue()[1][i]);
                p.setProperty("manual_" + m.getKey(), line.toString());
            }
        }
        java.io.StringWriter w = new java.io.StringWriter();
        try { p.store(w, null); } catch (java.io.IOException ignored) {}
        return w.toString();
    }

    private static void restoreProject(String blob) {
        java.util.Properties p = new java.util.Properties();
        try { p.load(new java.io.StringReader(blob)); } catch (java.io.IOException ignored) {}
        mode = Math.max(0, java.util.Arrays.asList(SOURCES).indexOf(p.getProperty("source", "bitwig")));
        slot = (int) Math.max(0, Math.min(SLOT_NAMES.length - 1, num(p.getProperty("manual_slot"), 0)));
        // before separate slots, the edited slot was also the one played
        playSlot = (int) Math.max(0, Math.min(SLOT_NAMES.length - 1, num(p.getProperty("manual_play_slot"), slot)));
        Overrides.restore(p);
        String cur = p.getProperty("current");
        if (cur != null) { int i = indexOf(Path.of(cur)); if (i >= 0) current = i; }
        fileAmounts = getAmounts(p, "", fileDefaults());
        manualAmounts = getAmounts(p, "m_", manualDefaults());
        savedShuffle = num(p.getProperty("bitwig_shuffle"), Double.NaN);
        savedAccent = num(p.getProperty("bitwig_accent"), Double.NaN);
        synchronized (State.class) {
            manual.clear();
            for (int b = 0; b < SLOT_NAMES.length * 16; b++) {
                if (b % 16 >= Groove.BASES.length) continue;
                String line = p.getProperty("manual_" + b);
                if (line == null || line.isBlank()) continue;
                String[] parts = line.split(",");
                double[][] m = manualKey(b);
                for (int i = 0; i < Math.min(parts.length, m[0].length); i++) {
                    String[] ov = parts[i].split(":");
                    if (ov.length == 2) { m[0][i] = num(ov[0], 0); m[1][i] = num(ov[1], 100); }
                }
            }
        }
    }

    private static void putAmounts(java.util.Properties p, String k, Groove.Amounts a) {
        p.setProperty(k + "strength", "" + a.strength); p.setProperty(k + "timing", "" + a.timing); p.setProperty(k + "velocity", "" + a.velocity);
        p.setProperty(k + "random", "" + a.random); p.setProperty(k + "quantize", "" + a.quantize); p.setProperty(k + "base", "" + a.base);
    }

    private static Groove.Amounts getAmounts(java.util.Properties p, String k, Groove.Amounts d) {
        Groove.Amounts a = new Groove.Amounts();
        a.strength = num(p.getProperty(k + "strength"), d.strength); a.timing = num(p.getProperty(k + "timing"), d.timing);
        a.velocity = num(p.getProperty(k + "velocity"), d.velocity); a.random = num(p.getProperty(k + "random"), d.random);
        a.quantize = num(p.getProperty(k + "quantize"), d.quantize); a.base = (int) num(p.getProperty(k + "base"), d.base);
        if (a.base < 0 || a.base >= Groove.BASES.length) a.base = 3;
        return a;
    }

    private static void save() {
        if (!restoring) ProjectStore.changed();
        try {
            bml.api.Settings s = MOD.settings();
            s.put("source", SOURCES[mode]);
            StringBuilder b = new StringBuilder();
            for (Entry e : list()) b.append(e.path).append('\n');
            s.put("grooves", b.toString());
            Entry e = selected();
            s.put("current", e == null ? "" : e.path.toString());
            writeAmounts(s, "", fileAmounts);
            writeAmounts(s, "m_", manualAmounts);
            synchronized (State.class) {
                for (java.util.Map.Entry<Integer, double[][]> m : manual.entrySet()) {
                    StringBuilder line = new StringBuilder();
                    for (int i = 0; i < m.getValue()[0].length; i++)
                        line.append(i == 0 ? "" : ",").append(m.getValue()[0][i]).append(':').append(m.getValue()[1][i]);
                    s.put("manual_" + m.getKey(), line.toString());
                }
            }
        } catch (Throwable t) { MOD.log("couldn't save groove settings", t); }
    }

    private static Groove.Amounts readAmounts(bml.api.Settings s, String p, Groove.Amounts d) {
        Groove.Amounts a = new Groove.Amounts();
        a.strength = num(s.get(p + "strength", "" + d.strength), d.strength);
        a.timing = num(s.get(p + "timing", "" + d.timing), d.timing);
        a.velocity = num(s.get(p + "velocity", "" + d.velocity), d.velocity);
        a.random = num(s.get(p + "random", "" + d.random), d.random);
        a.quantize = num(s.get(p + "quantize", "" + d.quantize), d.quantize);
        a.base = (int) num(s.get(p + "base", "" + d.base), d.base);
        if (a.base < 0 || a.base >= Groove.BASES.length) a.base = 3;
        return a;
    }

    private static void writeAmounts(bml.api.Settings s, String p, Groove.Amounts a) {
        s.put(p + "strength", Double.toString(a.strength));
        s.put(p + "timing", Double.toString(a.timing));
        s.put(p + "velocity", Double.toString(a.velocity));
        s.put(p + "random", Double.toString(a.random));
        s.put(p + "quantize", Double.toString(a.quantize));
        s.put(p + "base", Integer.toString(a.base));
    }

    private static double num(String s, double d) { try { return s == null ? d : Double.parseDouble(s); } catch (Exception e) { return d; } }
}
