package com.cimm.groove;

import bitwig.obf.HEL; // packs a clip's events into the byte array the engine plays (engine_playback_events)
import bitwig.obf.KAR; // the transport's "Groove on/off" (Play menu: Enable Groove)
import bitwig.obf.bpM; // a clip content timeline: JhA() packs it, qm() schedules a re-pack
import bitwig.obf.cIJ; // the Play menu's contents
import bitwig.obf.dWb; // a box layout
import bitwig.obf.dep; // a boolean value atom
import bitwig.obf.dfj; // one connection's outgoing change sender
import bitwig.obf.dve; // a widget
import bitwig.obf.n1;  // an instrument note (note_on_velocity = UNj(), 0..1)
import bitwig.obf.uWw; // one playable note occurrence (start FzB(), end MRY(), repeat velocity hUR(), id eLY())
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.ramona.core.oDc; // a document object
import com.bitwig.ramona.protocol.oRV; // a connection's request encoder

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Every hook of the mod (BML resolves a hook's method by name only, so they all live in this one class).
 *
 * Playback: the engine plays each clip from bytes that HEL packs on the Java side; HEL.cfM(List, uWw, int, double)
 * builds one note: velocity, then the note-start event (uWw.FzB()), then the note-end event (uWw.MRY()). Those reads
 * are replaced with grooved values, so every note clip follows the groove and the project's notes never move.
 *
 * Bitwig's own groove: while a groove file is used, the engine is told Groove on/off = off (the Play menu keeps showing
 * the real switch, which still turns the groove file on and off), so Shuffle/Accent never add to the file's timing.
 */
public final class Hooks {
    private Hooks() {}

    /** The note HEL is packing right now: its start, and its groove shift (computed once per note). */
    private static final ThreadLocal<double[]> current = ThreadLocal.withInitial(() -> new double[]{Double.NaN, 0});
    /** The note being packed: its groove and amounts ({Groove, Groove.Amounts}; a null groove plays it straight). */
    private static final ThreadLocal<Object[]> noteGroove = ThreadLocal.withInitial(() -> new Object[2]);
    /** Every clip timeline that has been packed, so a change can re-pack them all. */
    private static final Set<bpM> timelines = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static int logged, repacks, manualNotes, repackFails;

    // ------------------------------------------------------------------------------------------------ playback

    /** Re-packs every known clip (after the groove, its settings or the source change). UI thread. */
    /** Re-packs now, and again after 0.5, 1.5 and 3 s (clips that weren't ready the first time, as when a project opens). */
    static void repackSoon() {
        repackAll();
        for (long ms : new long[]{500, 1500, 3000}) {
            Thread t = new Thread(() -> {
                try { Thread.sleep(ms); com.bitwig.flt.app.CHR.Grk().asyncExec(Hooks::repackAll); } catch (Throwable ignored) {}
            }, "cimm-groove-repack");
            t.setDaemon(true); t.start();
        }
    }

    static void repackAll() {
        bpM[] all;
        synchronized (timelines) { all = timelines.toArray(new bpM[0]); }
        // Pack each clip now (as Bitwig's queued re-pack does: when attached and wanted, JhA()). qm() alone only queues
        // it until the project's next change, so a groove set while nothing else changes (a project opening) waited.
        int now = 0, queued = 0;
        for (bpM t : all) {
            try {
                if (t.K_() && t.hb_()) { t.JhA(); now++; } else { t.qm(); queued++; }
            } catch (Throwable e) {
                try { t.qm(); } catch (Throwable ignored) {}
                if (repackFails++ < 5) State.MOD.log("re-pack now failed, queued instead", e);
            }
        }
        if (repacks++ < 30 || repacks % 50 == 0) State.MOD.log("re-pack " + all.length + " clip(s) (" + now + " now, " + queued + " queued); source "
                + State.SOURCES[State.mode] + ", groove on " + State.grooveOn);
    }

    private static final Set<Object> projects = Collections.newSetFromMap(new WeakHashMap<>());


    /** A project window's transport toolbar is built (its PLAY button): that window's project. Taken once per project. */
    @Hook(id = "window-project", target = bitwig.obf.aCf.class, method = "cfM(LaBF;)Ldve;", at = At.ENTRY)
    public static void windowProject(@Arg(0) bitwig.obf.aBF window) {
        try {
            lastWindowOpened = System.currentTimeMillis();
            Object doc = window.hUR();
            if (doc instanceof com.bitwig.flt.document.core.master.g2L p) com.bitwig.flt.app.CHR.Grk().asyncExec(() -> project(p));
        } catch (Throwable t) { State.MOD.log("finding the window's project failed", t); }
    }

    /** Bitwig switched the project being heard (a project tab or window): the groove follows that project. */
    @Hook(id = "audible-project", target = com.bitwig.flt.prefs.EYY.class, method = "cfM(Lcom/bitwig/flt/prefs/DQC;)V", at = At.ENTRY)
    public static void audibleProject(@Arg(0) com.bitwig.flt.prefs.DQC which) {
        try {
            if (which == null) return;
            // while a project opens, Bitwig still names the one before as heard: don't follow that
            if (System.currentTimeMillis() - lastWindowOpened < 4000) return;
            Object doc = which.IwV().hUR();
            if (doc instanceof com.bitwig.flt.document.core.master.g2L p) com.bitwig.flt.app.CHR.Grk().asyncExec(() -> activate(p));
        } catch (Throwable t) { State.MOD.log("following the project switch failed", t); }
    }

    /** Make a project the current one: its settings, its Enable Groove, its clips re-packed, Bitwig's groove muted. */
    static void activate(com.bitwig.flt.document.core.master.g2L project) {
        try {
            project(project); // first time: follow it
            if (ProjectStore.current() == project) return;
            State.MOD.log("project switched: " + Integer.toHexString(System.identityHashCode(project)));
            State.currentDoc = docOf(project);
            com.bitwig.ramona.core.lBQ on = onValues.get(project);
            if (on != null) State.grooveOn = on.getBoolValue(); // this project's Enable Groove, before its settings are published
            ProjectStore.opened(project);
            repackSoon();
            Runnable sync = syncs.get(project);
            if (sync != null) switchCheck = sync;
        } catch (Throwable t) { State.MOD.log("switching project failed", t); }
    }

    private static volatile long lastWindowOpened;

    /** The Play menu opens in a project's window: that project is the one being worked on. */
    @Hook(id = "play-menu-project", target = cIJ.class, method = "MRY(LaBF;)V", at = At.ENTRY)
    public static void playMenuProject(@Arg(0) bitwig.obf.aBF window) { activateWindow(window); }

    static void activateWindow(Object window) {
        try {
            if (window instanceof bitwig.obf.aBF w && w.hUR() instanceof com.bitwig.flt.document.core.master.g2L p) activate(p);
        } catch (Throwable t) { State.MOD.log("following the window's project failed", t); }
    }

    /** Each Enable Groove switch's project document. */
    private static final Map<Object, Object> switchDoc = Collections.synchronizedMap(new WeakHashMap<>());
    static Object docOf(com.bitwig.flt.document.core.master.g2L p) {
        try { return ((com.bitwig.ramona.core.AVL) (Object) p).ULc(); } catch (Throwable t) { return null; }
    }

    /** Each project's Enable Groove value. */
    private static final Map<Object, com.bitwig.ramona.core.lBQ> onValues = Collections.synchronizedMap(new WeakHashMap<>());

    /** Each project's Enable Groove check. */
    private static final Map<Object, Runnable> syncs = Collections.synchronizedMap(new WeakHashMap<>());
    private static com.bitwig.flt.document.core.master.g2L watching;

    static void project(com.bitwig.flt.document.core.master.g2L project) {
        try {
            if (project == null || !projects.add(project)) return;
            Object groove = ((com.bitwig.flt.document.core.iface.pHd) project).ifN().ugU().UNj(); // transport > groove > on/off
            State.MOD.log("project window opened: " + Integer.toHexString(System.identityHashCode(project)));
            switchDoc.put(groove, docOf(project));
            onValues.put(project, ((bitwig.obf.LUI) groove).iCS());
            State.currentDoc = docOf(project);
            State.grooveOn = ((bitwig.obf.LUI) groove).iCS().getBoolValue();
            ProjectStore.opened(project);
            watching = project;
            watch((oDc) groove);
            muteBitwigGrooveAtOpen((oDc) groove, ((bitwig.obf.LUI) groove).iCS());
        } catch (Throwable t) { State.MOD.log("following the project failed", t); }
    }

    /** The events of a clip, about to be sorted and packed for the engine: audio events follow the groove. */
    @Hook(id = "audio-groove", target = HEL.class, method = "cfM(Ljava/util/List;Z)[B", at = At.ENTRY)
    public static void audioEvents(@Arg(0) java.util.List<?> events) { if (AudioGroove.ENABLED) AudioGroove.apply(events); }

    /** The content being packed (a clip's notes or audio): its own groove (Inspector) wins over the global one. */
    static final ThreadLocal<Object> packingContent = new ThreadLocal<>();

    @Hook(id = "pack-content-lSj", target = HEL.class, method = "cfM(LlSj;)[B", at = At.ENTRY)
    public static void packContent1(@Arg(0) Object c) { packingContent.set(c); }

    @Hook(id = "pack-content-ocL", target = HEL.class, method = "cfM(LocL;)[B", at = At.ENTRY)
    public static void packContent2(@Arg(0) Object c) { packingContent.set(c); }

    @Hook(id = "pack-content-iav", target = HEL.class, method = "cfM(Liav;)[B", at = At.ENTRY)
    public static void packContent3(@Arg(0) Object c) { packingContent.set(c); }

    @Hook(id = "pack-content-GVR", target = HEL.class, method = "cfM(LGVR;)[B", at = At.ENTRY)
    public static void packContent4(@Arg(0) Object c) { packingContent.set(c); }

    /** Audio hits being packed -> the project's audio event each was made from (for a groove of its own). */
    static final ThreadLocal<java.util.Map<Object, Object>> audioSources = ThreadLocal.withInitial(java.util.IdentityHashMap::new);
    private static final ThreadLocal<uWw> hitFrom = new ThreadLocal<>();

    /** HEL makes an audio hit's start (ZKD) from a playback event (uWw), whose cfM() is the project's audio event. */
    @Hook(id = "audio-hit-from", target = HEL.class, method = "cfM(Ljava/util/List;ILuWw;DDD)V", at = At.ENTRY)
    public static void audioHitFrom(@Arg(2) uWw event) { hitFrom.set(event); }

    @Hook(id = "audio-hit-made", target = HEL.class, method = "cfM(Ljava/util/List;ILuWw;DDD)V", at = At.REPLACE_CALL,
          callOwner = java.util.List.class, call = "add(Ljava/lang/Object;)Z", occurrence = 1)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean audioHitMade(java.util.List list, Object hit) {
        try {
            uWw from = hitFrom.get();
            if (from != null && Overrides.any()) audioSources.get().put(hit, from.cfM());
        } catch (Throwable ignored) {}
        return list.add(hit);
    }

    /** The clip being packed (HEL runs inside its JhA): its project's groove is the one used. */
    private static final ThreadLocal<Object> packingDoc = new ThreadLocal<>();

    @Hook(id = "track-timeline", target = bpM.class, method = "JhA()V", at = At.ENTRY)
    public static void packing(@This bpM timeline) {
        timelines.add(timeline);
        Object doc = null;
        try { doc = ((com.bitwig.ramona.core.AVL) (Object) timeline).ULc(); } catch (Throwable ignored) {}
        packingDoc.set(doc);
    }

    /** The groove settings for the clip being packed. */
    static State.PSnap packingSnap() { return State.snap(packingDoc.get()); }

    /**
     * The groove is one mapping of time: a note's start AND its end each go through it at their own position, so a
     * note ending where the next starts still ends exactly there (moving a whole note by its start's shift made back-to-
     * back notes of the same pitch overlap, and the first one's note-off cut the second). Random comes from the
     * position too (not the note), so ends and starts that meet stay together, and chords stay chords.
     */
    static double grooved(Groove g, double t) { return g.time(t, State.amounts(), seed(t)); }

    /** The same seed for the same position (to 1/960 beat). */
    private static long seed(double t) { return Math.round(t * 960) * 0x9E3779B97F4A7C15L; }

    @Hook(id = "note", target = HEL.class, method = "cfM(Ljava/util/List;LuWw;ID)V", at = At.ENTRY)
    public static void note(@Arg(1) uWw note) {
        double[] c = current.get();
        c[0] = Double.NaN; c[1] = 0;
        Object[] ng = noteGroove.get();
        ng[0] = null; ng[1] = null;
        try {
            // the note's own groove, its clip's or its track's (Inspector), else the global one
            Object source = note.Hnt;
            Overrides.Res r = Overrides.resolve(source);
            Groove g; Groove.Amounts amounts;
            if (r != null) { g = r.groove; amounts = r.amounts; }
            else { State.PSnap sn = packingSnap(); g = sn.active(); amounts = sn.amounts; }
            if (g == null) return;
            ng[0] = g; ng[1] = amounts;
            double t = note.FzB();
            double out = g.time(t, amounts, seed(t));
            if (t >= 0 && out < 0) out = 0; // nothing before the clip's start
            c[0] = t;
            c[1] = out - t;
            if (State.mode == State.MANUAL && manualNotes++ < 24) State.MOD.log("manual groove: note " + t + " -> " + out);
        } catch (Throwable e) { State.MOD.log("groove timing failed", e); }
    }

    @Hook(id = "note-start", target = HEL.class, method = "cfM(Ljava/util/List;LuWw;ID)V", at = At.REPLACE_CALL,
          callOwner = uWw.class, call = "FzB()D", occurrence = 1)
    public static double start(uWw note) { return note.FzB() + current.get()[1]; }

    @Hook(id = "note-end", target = HEL.class, method = "cfM(Ljava/util/List;LuWw;ID)V", at = At.REPLACE_CALL,
          callOwner = uWw.class, call = "MRY()D", occurrence = 1)
    public static double end(uWw note) {
        double end = note.MRY();
        double[] c = current.get();
        if (Double.isNaN(c[0])) return end;
        try {
            Object[] ng = noteGroove.get();
            Groove g = (Groove) ng[0];
            if (g == null) return end;
            double start = c[0] + c[1];
            // a note keeps at least half its length (a short note on a step pushed +50 % would otherwise shrink to nothing)
            return Math.max(g.time(end, (Groove.Amounts) ng[1], seed(end)), start + Math.max(0.004, (end - c[0]) * 0.5));
        } catch (Throwable e) { State.MOD.log("groove note end failed", e); return end + c[1]; }
    }

    @Hook(id = "note-velocity", target = HEL.class, method = "cfM(Ljava/util/List;LuWw;ID)V", at = At.REPLACE_CALL,
          callOwner = n1.class, call = "UNj()D", occurrence = 1)
    public static double velocity(n1 n) { return grooveVelocity(n.UNj()); }

    @Hook(id = "repeat-velocity", target = HEL.class, method = "cfM(Ljava/util/List;LuWw;ID)V", at = At.REPLACE_CALL,
          callOwner = uWw.class, call = "hUR()D", occurrence = 1)
    public static double repeatVelocity(uWw note) { return grooveVelocity(note.hUR()); }

    private static double grooveVelocity(double v) {
        try {
            Object[] ng = noteGroove.get();
            Groove g = (Groove) ng[0];
            double t = current.get()[0];
            if (g == null || Double.isNaN(t)) return v;
            return g.velocity(t, v, (Groove.Amounts) ng[1]);
        } catch (Throwable e) { State.MOD.log("groove velocity failed", e); return v; }
    }


    // ---- copied notes keep their groove: Bitwig copies a note's values from the original into the new note one by
    //      one (dey.cfM(to, from, ...): numbers such as position and velocity) ----
    @Hook(id = "note-copy", target = bitwig.obf.dey.class,
          method = "cfM(Lcom/bitwig/ramona/core/oDc;Lcom/bitwig/ramona/core/oDc;Lcom/bitwig/ramona/core/RKg;Lcom/bitwig/ramona/core/bbQ;)V",
          at = At.ENTRY)
    public static void valueCopied(@Arg(0) oDc to, @Arg(1) oDc from) { Overrides.copied(to, from); }

    // ---- a mark on notes with a groove of their own (Inspector): a small corner flag, drawn after Bitwig paints the note.
    //      Filled: the note has its own groove; hollow: the note is set to Off (plays straight).

    /** The rectangle of the note being painted: Bitwig checks it against what's visible right after building it. */
    private static final ThreadLocal<com.bitwig.base.geom.DoubleRectangle> painting = new ThreadLocal<>();

    @Hook(id = "note-mark-where", target = bitwig.obf.bro.class,
          method = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LkPd;DDDDDIJJZZZZZ)V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.base.geom.DoubleRectangle.class, call = "cfM(Lcom/bitwig/base/geom/DoubleRectangle;)Z", occurrence = 1)
    public static boolean noteWhere(com.bitwig.base.geom.DoubleRectangle note, com.bitwig.base.geom.DoubleRectangle visible) {
        painting.set(Overrides.any() ? note : null);
        return note.cfM(visible);
    }

    private static final bitwig.obf.iok MARK = new bitwig.obf.iok(1f, 1f, 1f, 0.92f),
            MARK_EDGE = new bitwig.obf.iok(0f, 0f, 0f, 0.55f);
    private static int markFails;

    /** Widgets the marks are drawn in (note editors, clip lanes, launcher clips): repainted when a groove changes, so
     *  a mark shows at once, not at Bitwig's next repaint. */
    private static final java.util.Set<bitwig.obf.dve> markWidgets = Collections.synchronizedSet(Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static volatile bitwig.obf.dve lastMarkWidget;

    static void markWidget(bitwig.obf.dve w) {
        if (w == null || w == lastMarkWidget) return;
        lastMarkWidget = w;
        markWidgets.add(w);
    }

    /** A groove changed (Inspector, copy): repaint everything that shows marks. UI thread. */
    static void repaintMarks() {
        try { resendClipShuffles(); } catch (Throwable ignored) {}
        bitwig.obf.dve[] all; synchronized (markWidgets) { all = markWidgets.toArray(new bitwig.obf.dve[0]); }
        for (bitwig.obf.dve w : all) { try { w.jbi(); } catch (Throwable ignored) {} }
    }

    /** The groove mark: a corner flag at (x, y). Filled: a groove of its own; hollow: Off (straight). */
    static void corner(com.bitwig.graphics.SYo g, double x, double top, double size, boolean off) {
        g.MRY(x, top); g.cfM(x + size, top); g.cfM(x, top + size); g.cfM(x, top);
        if (off) { g.Hnt(MARK); g.IwV(1.2); g.hbP(); }
        else {
            g.Hnt(MARK); g.wGc();
            g.MRY(x + size, top); g.cfM(x, top + size); g.Hnt(MARK_EDGE); g.IwV(1); g.hbP(); // its edge, against light colours
        }
    }

    /** Arranger clips: the clip lane paints a clip (boZ.cfM(Ob ...) -> cfM(kPd ...)), then its mark goes in the corner
     *  of the clip's rectangle (boZ.cfM(clip, x, y)). */
    @Hook(id = "clip-mark-arranger", target = bitwig.obf.boZ.class, method = "cfM(LOb;DDDDZ)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.boZ.class, call = "cfM(LkPd;DDDDZ)V", occurrence = 1)
    public static void arrangerClip(bitwig.obf.boZ painter, bitwig.obf.kPd clip, double a, double b, double c, double d, boolean z) {
        painter.cfM(clip, a, b, c, d, z); // Bitwig paints the clip
        if (!Overrides.any()) return;
        try {
            Overrides.Ov v = Overrides.get(clip);
            if (v == null || v.source == Overrides.DEFAULT) return;
            bitwig.obf.bpa p = (bitwig.obf.bpa) (Object) painter;
            markWidget(p.MRY);
            com.bitwig.base.geom.DoubleRectangle r = painter.cfM(clip, a, b);
            if (r == null || r.FzB < 6 || r.IwV < 6) return;
            corner(p.cfM, r.MRY + 2, r.Hnt + 2, Math.min(9, Math.min(r.FzB, r.IwV) - 4), v.source == Overrides.OFF);
        } catch (Throwable t) { if (markFails++ < 3) State.MOD.log("arranger clip mark failed", t); }
    }

    /** Audio events (in the audio editor and clips): Bitwig paints the event, then its mark goes in the top-left corner
     *  of the event's rectangle (the painter's last argument). */
    private static int audioMarkLogs;

    @Hook(id = "audio-event-mark", target = bitwig.obf.bro.class,
          method = "cfM(LlbB;LLA1;LMQG;LiKU;DDDDDDIZZZZZZ)V", at = At.REPLACE_CALL, callOwner = bitwig.obf.bro.class,
          call = "cfM(Lcom/bitwig/graphics/SYo;LlbB;LLA1;LMQG;LiKU;IDDDDDDZZZZZLcom/bitwig/base/geom/DoubleRectangle;)V", occurrence = 1)
    public static void paintAudioEvent(bitwig.obf.bro painter, com.bitwig.graphics.SYo g, bitwig.obf.lbB l, bitwig.obf.LA1 event,
            bitwig.obf.MQG m, bitwig.obf.iKU k, int i, double a, double b, double c, double d, double e, double f,
            boolean z1, boolean z2, boolean z3, boolean z4, boolean z5, com.bitwig.base.geom.DoubleRectangle r) {
        painter.cfM(g, l, event, m, k, i, a, b, c, d, e, f, z1, z2, z3, z4, z5, r); // Bitwig paints the audio event
        if (!Overrides.any() || r == null) return;
        try {
            Overrides.Ov v = Overrides.get(event);
            if (v == null) { Object original = standIns.get(event); if (original != null) v = Overrides.get(original); }
            if (v == null || v.source == Overrides.DEFAULT) return;
            markWidget(((bitwig.obf.bpa) (Object) painter).MRY);
            if (audioMarkLogs++ < 2) State.MOD.log("audio event mark at x " + r.MRY + " y " + r.Hnt + " w " + r.FzB + " h " + r.IwV
                    + " (args " + a + ", " + b + ", " + c + ", " + d + ", " + e + ", " + f + ")");
            double size = Math.min(9, Math.min(r.FzB, r.IwV) - 4);
            if (size < 3) return;
            corner(g, r.MRY + 2, r.Hnt + 2, size, v.source == Overrides.OFF);
        } catch (Throwable t) { if (markFails++ < 3) State.MOD.log("audio event mark failed", t); }
    }

    /** Selected / edited notes are painted as a stand-in copy (Bitwig's preview of the note): copy -> original. */
    private static final java.util.Map<Object, Object> standIns = Collections.synchronizedMap(new java.util.WeakHashMap<>());

    @Hook(id = "note-stand-in", target = bitwig.obf.wMV.class, method = "cfM(Llh;)V", at = At.ENTRY)
    public static void standIn(@This bitwig.obf.wMV original, @Arg(0) Object copy) {
        if (copy != null && copy != original) standIns.put(copy, original);
    }

    /** Launcher clips: after the clip widget paints (over its slot), the mark goes in its top-left corner. */
    @Hook(id = "clip-mark-launcher", target = bitwig.obf.bdm.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V", at = At.OVERRIDE_AFTER_SUPER)
    public static void launcherClipMark(@This bitwig.obf.bdm clipWidget, @Arg(0) com.bitwig.graphics.SYo g) {
        if (!Overrides.any()) return;
        try {
            markWidget(clipWidget);
            Object clip = ((bitwig.obf.bdn) (Object) clipWidget).LzO();
            Overrides.Ov v = Overrides.get(clip);
            if (v == null || v.source == Overrides.DEFAULT) return;
            corner(g, 2, 2, 9, v.source == Overrides.OFF);
        } catch (Throwable t) { if (markFails++ < 3) State.MOD.log("clip mark failed", t); }
    }

    @Hook(id = "note-mark-brv", target = bitwig.obf.brv.class, method = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LhnT;LiKU;IDDDDDZZZZZLcom/bitwig/base/geom/DoubleRectangle;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.brv.class, call = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LkPd;DDDDDIJJZZZZZ)V", occurrence = 1)
    public static void paintNote_brv(bitwig.obf.brv painter, com.bitwig.graphics.SYo g, bitwig.obf.Ibv note, bitwig.obf.kPd k,
            double a, double b, double c, double d, double e, int i, long y, long h,
            boolean z1, boolean z2, boolean z3, boolean z4, boolean z5) {
        painter.cfM(g, note, k, a, b, c, d, e, i, y, h, z1, z2, z3, z4, z5); // Bitwig paints the note
        markWidget(((bitwig.obf.bpa) (Object) painter).MRY);
        noteMark(g, note);                                                // then its groove mark on top
    }

    @Hook(id = "note-mark-brw", target = bitwig.obf.brw.class, method = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LhnT;LiKU;IDDDDDZZZZZLcom/bitwig/base/geom/DoubleRectangle;)V", at = At.REPLACE_CALL,
          callOwner = bitwig.obf.brw.class, call = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LkPd;DDDDDIJJZZZZZ)V", occurrence = 1)
    public static void paintNote_brw(bitwig.obf.brw painter, com.bitwig.graphics.SYo g, bitwig.obf.Ibv note, bitwig.obf.kPd k,
            double a, double b, double c, double d, double e, int i, long y, long h,
            boolean z1, boolean z2, boolean z3, boolean z4, boolean z5) {
        painter.cfM(g, note, k, a, b, c, d, e, i, y, h, z1, z2, z3, z4, z5); // Bitwig paints the note
        markWidget(((bitwig.obf.bpa) (Object) painter).MRY);
        noteMark(g, note);                                                // then its groove mark on top
    }

    static void noteMark(com.bitwig.graphics.SYo g, bitwig.obf.Ibv note) {
        com.bitwig.base.geom.DoubleRectangle r = painting.get();
        if (r == null) return;
        painting.set(null);
        try {
            Overrides.Ov v = Overrides.get(note);
            if (v == null) { Object original = standIns.get(note); if (original != null) v = Overrides.get(original); }
            if (v == null || v.source == Overrides.DEFAULT) return;
            double size = Math.min(8, Math.min(r.IwV - 2, r.FzB - 2)); // r: x MRY, y Hnt, width FzB, height IwV
            if (size < 3) return;
            corner(g, r.MRY + 1, r.Hnt + 1, size, v.source == Overrides.OFF);
        } catch (Throwable t) { if (markFails++ < 3) State.MOD.log("note mark failed", t); }
    }

    // ------------------------------------------------------------------------------------------------ Bitwig's own groove

    private static final class Sent { final oRV enc; final dep atom; final oDc obj; boolean value; Sent(oRV e, dep a, oDc o) { enc = e; atom = a; obj = o; } }
    /** The Groove on/off values sent to engines, per connection and object (for re-sending when the source changes). */
    private static final List<Sent> switches = new ArrayList<>();

    @Hook(id = "groove-switch", target = dfj.class, method = "cfM(Ldep;Lcom/bitwig/ramona/core/oDc;Z)V", at = At.REPLACE_CALL,
          callOwner = oRV.class, call = "cfM(Ldep;Lcom/bitwig/ramona/core/oDc;Z)V", occurrence = 1)
    public static void sendBool(oRV enc, dep atom, oDc obj, boolean v) {
        boolean out = v;
        noteConnection(enc, obj);
        try {
            if (atom == clipShuffle()) {
                rememberClip(enc, atom, obj);
                if (v && Overrides.playsOwnGroove(obj)) out = false;
            }
            if (obj instanceof KAR) {
                remember(enc, atom, obj, v); // also starts following Bitwig's own value, which decides
                dfj sender = senderFor(obj, switchDoc.get(obj));
                if (sender != null) synchronized (bitwigSwitches) {
                    boolean known = false;
                    for (Object[] s2 : bitwigSwitches) if (s2[0] == sender && s2[2] == obj) known = true;
                    if (!known) bitwigSwitches.add(new Object[]{sender, atom, obj});
                }
                Object d = switchDoc.get(obj);
                if (State.snap(d).usesGroove()) out = false;
                if (switchLogs++ < 30) State.MOD.log("Bitwig groove switch sent: " + v + " -> engine " + out + " (project " + (d == null ? "unknown" : "known") + ", sender " + (sender != null) + ")");
            }
        } catch (Throwable t) { State.MOD.log("groove switch failed", t); }
        enc.cfM(atom, obj, out);
    }

    private static int switchLogs;

    private static void remember(oRV enc, dep atom, oDc obj, boolean v) {
        synchronized (switches) {
            for (Sent s : switches) if (s.enc == enc && s.obj == obj) { s.value = v; return; }
            Sent s = new Sent(enc, atom, obj);
            s.value = v;
            switches.add(s);
            State.MOD.log("Groove on/off seen: " + v + " (" + switches.size() + " connection(s))");
            watch(obj);
        }
    }

    // Belt and braces: while a groove of ours is used, the engine also gets Shuffle amount 0 and Accent amount 0, so
    // Bitwig's own groove can't add to ours even if it were on. The real values are kept and sent back for Bitwig mode.
    private static final class SentAmount { final oRV enc; final bitwig.obf.dey atom; final oDc obj; double value;
        SentAmount(oRV e, bitwig.obf.dey a, oDc o) { enc = e; atom = a; obj = o; } }
    private static final List<SentAmount> amounts = new ArrayList<>();

    private static boolean bitwigGrooveAmount(oDc obj) { return obj instanceof bitwig.obf.j12 || obj instanceof bitwig.obf.I2d; }

    @Hook(id = "groove-amounts", target = dfj.class, method = "cfM(Ldey;Lcom/bitwig/ramona/core/oDc;D)V", at = At.REPLACE_CALL,
          callOwner = oRV.class, call = "cfM(Ldey;Lcom/bitwig/ramona/core/oDc;D)V", occurrence = 1)
    public static void sendDouble(oRV enc, bitwig.obf.dey atom, oDc obj, double v) {
        double out = v;
        noteConnection(enc, obj);
        try {
            if (bitwigGrooveAmount(obj)) {
                synchronized (amounts) {
                    SentAmount found = null;
                    for (SentAmount a : amounts) if (a.enc == enc && a.obj == obj) found = a;
                    if (found == null) { found = new SentAmount(enc, atom, obj); amounts.add(found); }
                    found.value = v;
                }
                if (State.usesGroove()) out = 0;
            }
        } catch (Throwable t) { State.MOD.log("groove amount failed", t); }
        enc.cfM(atom, obj, out);
    }

    private static void noteConnection(oRV enc, oDc obj) {}
    /** Bitwig's Enable Groove objects and the sender of their engine: {dfj, dep atom, oDc object}. */
    private static final List<Object[]> bitwigSwitches = new ArrayList<>();

    /** Every connection's change sender seen (each belongs to one document, its dfn's eLY()). */
    private static final Set<dfj> senders = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @Hook(id = "sender-double", target = dfj.class, method = "cfM(Ldey;Lcom/bitwig/ramona/core/oDc;D)V", at = At.ENTRY)
    public static void senderDouble(@This dfj s) { senders.add(s); }

    @Hook(id = "sender-bool", target = dfj.class, method = "cfM(Ldep;Lcom/bitwig/ramona/core/oDc;Z)V", at = At.ENTRY)
    public static void senderBool(@This dfj s) { senders.add(s); }

    @Hook(id = "sender-int", target = dfj.class, method = "cfM(LdeH;Lcom/bitwig/ramona/core/oDc;I)V", at = At.ENTRY)
    public static void senderInt(@This dfj s) { senders.add(s); }

    private static java.lang.reflect.Field connectionField;

    /** The connection (encoder) of the engine that holds this object's document, or null: never another one (an
     *  engine told about an object it doesn't have crashes). */
    static dfj senderFor(oDc obj, Object doc) {
        try {
            if (doc == null) return null;
            if (connectionField == null) {
                for (java.lang.reflect.Field f : dfj.class.getDeclaredFields())
                    if (com.bitwig.ramona.protocol.Dgc.class.isAssignableFrom(f.getType())) { f.setAccessible(true); connectionField = f; }
            }
            dfj[] all; synchronized (senders) { all = senders.toArray(new dfj[0]); }
            for (dfj s : all) {
                Object c = connectionField.get(s);
                if (c instanceof com.bitwig.ramona.protocol.Dgc d && d.eLY() == doc) return s;
            }
        } catch (Throwable t) { State.MOD.log("finding the engine connection failed", t); }
        return null;
    }

    /**
     * When a project opens, the engine starts with the project's Enable Groove as it is (on): with a groove of ours in
     * use, Bitwig's own groove would then play over it until the switch is next sent. So: send the engine "off" for it
     * now, over the project's connection, as soon as that connection has been seen (retrying for a few seconds).
     */
    private static void muteBitwigGrooveAtOpen(oDc grooveSwitch, com.bitwig.ramona.core.lBQ on) {
        Thread t = new Thread(() -> {
            for (int i = 0; i < 40; i++) {
                try { Thread.sleep(300); } catch (InterruptedException e) { return; }
                dfj sender = senderFor(grooveSwitch, switchDoc.get(grooveSwitch));
                if (sender == null) continue;
                try {
                    com.bitwig.flt.app.CHR.Grk().asyncExec(() -> {
                        try {
                            dep atom = on.TMF();
                            synchronized (bitwigSwitches) {
                                boolean known = false;
                                for (Object[] s2 : bitwigSwitches) if (s2[0] == sender && s2[2] == grooveSwitch) known = true;
                                if (!known) bitwigSwitches.add(new Object[]{sender, atom, grooveSwitch});
                            }
                            // Bitwig's own sender (its checks, flushed with its own traffic); our hook makes it "off"
                            boolean mute = State.snap(switchDoc.get(grooveSwitch)).usesGroove();
                            if (mute) sender.cfM(atom, grooveSwitch, on.getBoolValue());
                            State.MOD.log("project's Bitwig groove " + (mute ? "muted" : "left as it is") + " at open");
                        } catch (Throwable e) { State.MOD.log("muting Bitwig's groove at open failed", e); }
                    });
                } catch (Throwable ignored) {}
                return;
            }
            State.MOD.log("this project's engine connection wasn't seen: Bitwig's groove left as it is (toggle Enable Groove once)");
        }, "cimm-groove-mute-at-open");
        t.setDaemon(true);
        t.start();
    }

    /** The last project's Enable Groove check; run twice a second on the UI thread, because a project that opens sets
     *  the value without telling its listeners. */
    private static volatile Runnable switchCheck;
    private static java.util.concurrent.ScheduledExecutorService checker;

    private static synchronized void startChecking() {
        if (checker != null) return;
        checker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cimm-groove-switch-check"); t.setDaemon(true); return t;
        });
        checker.scheduleAtFixedRate(() -> {
            Runnable r = switchCheck;
            try {
                com.bitwig.flt.app.CHR.Grk().asyncExec(() -> {
                    if (r != null) r.run();
                    try { ProjectStore.check(); } catch (Throwable ignored) {}
                    try { GrooveInspector.tick(); } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        }, 500, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Bitwig's Enable Groove objects already watched. */
    private static final Set<Object> watched = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Follows Bitwig's own Enable Groove value (not only what is sent to the engine: when a project opens, the value
     * sent first can be the default, and the project's own comes by another way). Its listener re-packs the clips.
     */
    private static void watch(oDc obj) {
        try {
            if (!(obj instanceof bitwig.obf.LUI) || !watched.add(obj)) return;
            com.bitwig.ramona.core.lBQ on = ((bitwig.obf.LUI) obj).iCS();
            Runnable[] self = new Runnable[1];
            Object doc = switchDoc.get(obj);
            boolean[] last = {!on.getBoolValue()};
            Runnable sync = () -> {
                try {
                    boolean v = on.getBoolValue();
                    if (v != last[0]) {
                        last[0] = v;
                        State.grooveOnFor(doc, v);
                        State.MOD.log("Enable Groove: " + v);
                        repackSoon();
                        State.repaint();
                    }
                } catch (Throwable t) { State.MOD.log("reading Enable Groove failed", t); }
            };
            on.addListener(e -> sync.run());
            if (watching != null) syncs.put(watching, sync);
            sync.run();
            switchCheck = sync;
            startChecking();
        } catch (Throwable t) { State.MOD.log("watching Enable Groove failed", t); }
    }

    /** Sends Groove on/off again to every engine: the real value in Bitwig mode, off while a groove file is used. */
    private static int resendLogs;

    static void resendGrooveSwitch() {
        Sent[] all; synchronized (switches) { all = switches.toArray(new Sent[0]); }
        SentAmount[] am; synchronized (amounts) { am = amounts.toArray(new SentAmount[0]); }
        int viaSender = 0, passed = 0;
        // Through each engine connection's OWN change sender (dfj), as when the knob is moved: written straight to the
        // encoder it never reached the engine, and another connection's sender filters it out ("passes false": that
        // connection doesn't sync the value). The send hooks above make the value off / 0 while a groove of ours is used.
        for (Sent s : all) {
            try {
                dfj d = senderOf(s.enc);
                if (d == null) continue;
                viaSender++;
                if (passes(d, s.atom, s.obj)) passed++;
                d.cfM(s.atom, s.obj, ((bitwig.obf.LUI) s.obj).iCS().getBoolValue());
            } catch (Throwable t) { State.MOD.log("re-sending Groove on/off failed", t); }
        }
        for (SentAmount a : am) {
            try {
                dfj d = senderOf(a.enc);
                if (d == null) continue;
                viaSender++;
                if (passes(d, a.atom, a.obj)) passed++;
                d.cfM(a.atom, a.obj, a.atom.lYc(a.obj));
            } catch (Throwable t) { State.MOD.log("re-sending Shuffle/Accent failed", t); }
        }
        if (resendLogs++ < 20) State.MOD.log("re-sending Bitwig's groove (" + (State.usesGroove() ? "off, Shuffle/Accent 0" : "as it is")
                + "): " + all.length + " switch(es), " + am.length + " amount(s), " + viaSender + " through their sender, " + passed + " passed");
    }

    /** The change sender (dfj) that writes to this encoder: one per connection. */
    private static dfj senderOf(oRV enc) {
        try {
            if (encoderField == null)
                for (java.lang.reflect.Field f : dfj.class.getDeclaredFields())
                    if (oRV.class.isAssignableFrom(f.getType())) { f.setAccessible(true); encoderField = f; }
            if (encoderField == null) return null;
            dfj[] all; synchronized (senders) { all = senders.toArray(new dfj[0]); }
            for (dfj d : all) if (encoderField.get(d) == enc) return d;
        } catch (Throwable t) { State.MOD.log("finding a connection's sender failed", t); }
        return null;
    }

    private static boolean passes(dfj d, Object atom, oDc obj) {
        try {
            if (connectionField == null)
                for (java.lang.reflect.Field f : dfj.class.getDeclaredFields())
                    if (com.bitwig.ramona.protocol.Dgc.class.isAssignableFrom(f.getType())) { f.setAccessible(true); connectionField = f; }
            return connectionField.get(d) instanceof com.bitwig.ramona.protocol.Dgc c && c.cfM((bitwig.obf.deN) atom, obj);
        } catch (Throwable t) { return false; }
    }

    // ---- each clip's own Shuffle switch: off towards the engine while the clip plays a groove of its own (or holds
    //      notes / audio events with one, or is on a track with one), so Bitwig's shuffle never adds to it. The clip's
    //      setting in the project is left as it is. ----
    private static dep clipShuffleAtom;
    private static boolean clipShuffleMissing;

    static dep clipShuffle() {
        if (clipShuffleAtom == null && !clipShuffleMissing) {
            try { clipShuffleAtom = bitwig.obf.pnB.Hnt.uWE(); } // the clip schema's "shuffle"
            catch (Throwable t) { clipShuffleMissing = true; State.MOD.log("clip Shuffle switch not found", t); }
        }
        return clipShuffleAtom;
    }

    private static final List<Object[]> clipSwitches = new ArrayList<>(); // {oRV, dep, oDc}

    private static void rememberClip(oRV enc, dep atom, oDc clip) {
        synchronized (clipSwitches) {
            for (Object[] c : clipSwitches) if (c[0] == enc && c[2] == clip) return;
            clipSwitches.add(new Object[]{enc, atom, clip});
            if (clipSwitches.size() > 4096) clipSwitches.remove(0);
        }
    }

    /** Grooves changed: every clip's Shuffle switch goes to the engine again (through its connection's own sender, so
     *  the send hook decides on / off). UI thread. */
    static void resendClipShuffles() {
        Object[][] all; synchronized (clipSwitches) { all = clipSwitches.toArray(new Object[0][]); }
        for (Object[] c : all) {
            try {
                dfj d = senderOf((oRV) c[0]);
                if (d == null) continue;
                dep atom = (dep) c[1]; oDc clip = (oDc) c[2];
                if (clip.J_() == null) continue; // deleted
                d.cfM(atom, clip, atom.lYc(clip));
            } catch (Throwable t) { State.MOD.log("re-sending a clip's Shuffle failed", t); }
        }
    }

    // ---- Whole-project syncs to the engine (engine reset, the middle of every Bounce, project open)
    //
    // When the engine (re)starts it gets every object written out in full, not as value changes, so the change hooks
    // above never see Enable Groove or Shuffle/Accent: Bitwig's own groove came back on under ours (a bounce had the
    // groove file's swing plus Bitwig's shuffle). Each engine connection writes with its own serialization context,
    // which its change sender hands out (dfj.Hnt()); writes with one of those contexts get Enable Groove = off and
    // Shuffle/Accent = 0 while a groove of ours is used. Saving the project uses other contexts and is left alone.

    private static final Set<Object> engineContexts = Collections.synchronizedSet(Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    private static final ThreadLocal<Object> writing = new ThreadLocal<>();
    private static int syncLogs;

    @Hook(id = "engine-context", target = dfj.class, method = "Hnt()Lcom/bitwig/ramona/serial/iYN;", at = At.WRAP_RETURN)
    public static com.bitwig.ramona.serial.iYN engineContext(@Return com.bitwig.ramona.serial.iYN ctx, @This dfj sender) {
        try {
            senders.add(sender);
            if (ctx != null && engineContexts.size() < 64 && engineContexts.add(ctx)) {
                if (encoderField == null)
                    for (java.lang.reflect.Field f : dfj.class.getDeclaredFields())
                        if (oRV.class.isAssignableFrom(f.getType())) { f.setAccessible(true); encoderField = f; }
                Object enc = encoderField == null ? null : encoderField.get(sender);
                if (enc instanceof oRV e) contextEncoder.put(ctx, e);
            }
        } catch (Throwable t) { State.MOD.log("noting an engine connection failed", t); }
        return ctx;
    }

    /** Each engine context's encoder: Shuffle/Accent/Enable Groove written to that engine can be sent again to it. */
    private static final Map<Object, oRV> contextEncoder = Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static java.lang.reflect.Field encoderField;

    @Hook(id = "write-bool-context", target = dep.class,
          method = "cfM(Lcom/bitwig/ramona/core/oDc;Lcom/bitwig/ramona/serial/iYN;Lcom/bitwig/base/serial/Wkj;Lcom/bitwig/ramona/serial/RYd;)V", at = At.ENTRY)
    public static void writeBoolContext(@Arg(1) com.bitwig.ramona.serial.iYN ctx) { writing.set(ctx); }

    @Hook(id = "write-bool", target = dep.class,
          method = "cfM(Lcom/bitwig/ramona/core/oDc;Lcom/bitwig/ramona/serial/iYN;Lcom/bitwig/base/serial/Wkj;Lcom/bitwig/ramona/serial/RYd;)V",
          at = At.REPLACE_CALL, callOwner = dep.class, call = "lYc(Lcom/bitwig/ramona/core/oDc;)Z", occurrence = 1)
    public static boolean writeBool(dep atom, oDc obj) {
        boolean v = atom.lYc(obj);
        try {
            if (obj instanceof KAR) { oRV enc = contextEncoder.get(writing.get()); if (enc != null) remember(enc, atom, obj, v); }
            if (atom == clipShuffle() && engineContexts.contains(writing.get())) {
                oRV enc = contextEncoder.get(writing.get());
                if (enc != null) rememberClip(enc, atom, obj);
                if (v && Overrides.playsOwnGroove(obj)) return false;
            }
            if (v && obj instanceof KAR && engineContexts.contains(writing.get()) && State.snap(switchDoc.get(obj)).usesGroove()) {
                if (syncLogs++ < 20) State.MOD.log("engine sync: Bitwig's own groove written as off");
                return false;
            }
        } catch (Throwable t) { State.MOD.log("engine sync (groove switch) failed", t); }
        return v;
    }

    @Hook(id = "write-double-context", target = bitwig.obf.dey.class,
          method = "cfM(Lcom/bitwig/ramona/core/oDc;Lcom/bitwig/ramona/serial/iYN;Lcom/bitwig/base/serial/Wkj;Lcom/bitwig/ramona/serial/RYd;)V", at = At.ENTRY)
    public static void writeDoubleContext(@Arg(1) com.bitwig.ramona.serial.iYN ctx) { writing.set(ctx); }

    @Hook(id = "write-double", target = bitwig.obf.dey.class,
          method = "cfM(Lcom/bitwig/ramona/core/oDc;Lcom/bitwig/ramona/serial/iYN;Lcom/bitwig/base/serial/Wkj;Lcom/bitwig/ramona/serial/RYd;)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.dey.class, call = "lYc(Lcom/bitwig/ramona/core/oDc;)D", occurrence = 1)
    public static double writeDouble(bitwig.obf.dey atom, oDc obj) {
        double v = atom.lYc(obj);
        try {
            if (bitwigGrooveAmount(obj)) {
                oRV enc = contextEncoder.get(writing.get());
                if (enc != null) synchronized (amounts) {
                    SentAmount found = null;
                    for (SentAmount a : amounts) if (a.enc == enc && a.obj == obj) found = a;
                    if (found == null) { found = new SentAmount(enc, atom, obj); amounts.add(found); }
                    found.value = v;
                }
            }
            if (v != 0 && bitwigGrooveAmount(obj) && engineContexts.contains(writing.get()) && State.usesGroove()) {
                if (syncLogs++ < 20) State.MOD.log("engine sync: Bitwig's " + (obj instanceof bitwig.obf.j12 ? "shuffle" : "accent") + " written as 0");
                return 0;
            }
        } catch (Throwable t) { State.MOD.log("engine sync (groove amount) failed", t); }
        return v;
    }

    // ------------------------------------------------------------------------------------------------ Undo / Redo

    /** When Bitwig's own undo steps were made (a stack, so Undo / Redo can keep the two histories in time order). */
    private static final java.util.ArrayDeque<Long> bitwigSteps = new java.util.ArrayDeque<>(), bitwigRedo = new java.util.ArrayDeque<>();

    /** Bitwig closes an undo step: remember when. */
    @Hook(id = "bitwig-step", target = com.bitwig.ramona.core.XKG.class, method = "cfM(Z)Z", at = At.ENTRY)
    public static void bitwigStep() {
        ProjectStore.refreshInStep(); // the stored note / clip grooves follow every edit, inside the same step
        bitwigSteps.push(System.currentTimeMillis());
        while (bitwigSteps.size() > 500) bitwigSteps.removeLast();
        bitwigRedo.clear();
    }

    /** Bitwig's Undo (Cmd+Z): the groove change if it is the newer of the two, else Bitwig's own. */
    @Hook(id = "undo", target = bitwig.obf.aBW.class, method = "Ktv()V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.flt.document.core.iface.pHd.class, call = "Mb2()V", occurrence = 1)
    public static void undo(com.bitwig.flt.document.core.iface.pHd doc) {
        try { ProjectStore.flush(); } catch (Throwable ignored) {} // a groove change made just now is its own step first
        doc.Mb2(); // the groove settings are in the project: Bitwig's own undo covers them
        try { ProjectStore.check(); } catch (Throwable ignored) {} // corners, Inspector and playback follow at once
    }

    /** Bitwig's Redo (Cmd+Shift+Z): whichever undone change is the older (the one undone last). */
    @Hook(id = "redo", target = bitwig.obf.aBW.class, method = "d3()V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.flt.document.core.iface.pHd.class, call = "LIK()V", occurrence = 1)
    public static void redo(com.bitwig.flt.document.core.iface.pHd doc) {
        doc.LIK();
        try { ProjectStore.check(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------------------------------------ Quantize window

    @Hook(id = "probe-quantize-open", target = com.bitwig.flt.widget.core.timeline.huU.class,
          method = "cfM(LHQy;LICh;Ldve;)Lcom/bitwig/flt/widget/core/timeline/huU;", at = At.ENTRY)
    public static void probeQuantizeOpen() { State.MOD.log("Quantize window: opening (huU)"); }


    /** Bitwig's Quantize… window content, in tabs: Quantize (Bitwig's) / Groove File / Manual. */
    @Hook(id = "quantize-tabs", target = com.bitwig.flt.widget.core.timeline.huU.class,
          method = "MRY(LHQy;LICh;Ldve;)V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.flt.widget.core.timeline.huU.class, call = "IwV(Ldve;)V", occurrence = 1)
    public static void quantizeTabs(com.bitwig.flt.widget.core.timeline.huU dialog, dve content) {
        State.MOD.log("Quantize window: adding tabs to " + (content == null ? "null" : content.getClass().getName()));
        dialog.IwV(QuantizeTabs.build(content));
    }

    /** OK: Bitwig's quantize on the Quantize tab; the groove into the notes on the others. */
    @Hook(id = "quantize-ok", target = com.bitwig.flt.widget.core.timeline.huU.class, method = "cfM(I)V", at = At.REPLACE_CALL,
          callOwner = com.bitwig.flt.widget.core.timeline.huU.class, call = "cfM(LICh;)V", occurrence = 1)
    public static void quantizeOk(com.bitwig.flt.widget.core.timeline.huU dialog, bitwig.obf.ICh selection) {
        State.MOD.log("Quantize window OK on tab " + QuantizeTabs.tab);
        if (QuantizeTabs.tab == QuantizeTabs.QUANTIZE) {
            selection.tco(); // Bitwig's own quantize (what the window's OK does)
            return;
        }
        QuantizeTabs.commit(selection);
    }

    // ------------------------------------------------------------------------------------------------ Play menu

    /** The Play menu's GROOVE section stacks Shuffle and Accent in the first vertical box it makes: our rows go around it. */
    @Hook(id = "groove-section", target = cIJ.class, method = "MRY(LaBF;)V", at = At.REPLACE_CALL,
          callOwner = dWb.class, call = "MRY(I[Ldve;)LdWb;", occurrence = 1)
    public static dWb section(int gap, dve[] rows) {
        dWb bitwig = dWb.MRY(gap, rows);
        try {
            State.load();
            return Ui.build(gap, bitwig);
        } catch (Throwable t) {
            State.MOD.log("couldn't add the groove file controls", t);
            return bitwig;
        }
    }
}
