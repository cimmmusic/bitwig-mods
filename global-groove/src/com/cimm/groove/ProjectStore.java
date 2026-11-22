package com.cimm.groove;

import com.bitwig.flt.app.CHR; // the application: asyncExec runs on the UI thread
import com.bitwig.flt.document.core.master.g2L; // a project

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The groove settings live in the project itself, so they are saved with it, travel with it, and Bitwig's own Undo /
 * Redo covers them: a tagged block at the end of the master track's comment (any comment text of the user's above it
 * is left as it is). Each change rewrites the block as one Bitwig undo step ("Change Groove"); changes less than 400 ms apart
 * (a knob drag) are one step. When the block changes by any other way (Undo, Redo, opening a project), the settings
 * follow it.
 */
final class ProjectStore {
    private ProjectStore() {}

    static final String MARK = "\n\n[Global Groove settings, do not edit]\n";

    private static volatile g2L project;
    /** The comment as last written or read by us: anything else is a change from outside (Undo, Redo). */
    private static volatile String lastSeen;
    private static volatile int pending;

    private static boolean loggedMaster;

    /** The project's master track (its top group). */
    static com.bitwig.flt.document.core.master.cXR master(g2L p) {
        try {
            com.bitwig.flt.document.core.master.cXR t = p.PEn(); // the top group: the master track (a track: has a comment)
            if (t != null && !loggedMaster) { loggedMaster = true; State.MOD.log("groove settings kept in the master track's comment"); }
            return t;
        } catch (Throwable t) { State.MOD.log("no master track", t); return null; }
    }

    private static String comment(g2L p) {
        try { com.bitwig.flt.document.core.master.cXR m = master(p); String c = m == null ? null : m.AgD(); return c == null ? "" : c; } catch (Throwable t) { return ""; }
    }

    /** The settings block in a comment, decoded, or null when there is none. */
    static String blob(String comment) {
        int at = comment.lastIndexOf(MARK);
        if (at < 0) return null;
        try { return new String(Base64.getDecoder().decode(comment.substring(at + MARK.length()).trim()), StandardCharsets.UTF_8); }
        catch (Throwable t) { return null; }
    }

    private static String userText(String comment) {
        int at = comment.lastIndexOf(MARK);
        return at < 0 ? comment : comment.substring(0, at);
    }

    static g2L current() { return project; }

    /** A project opened or became the current one (a tab switch): its groove settings, or the defaults when it has
     *  none. A change still waiting to be written goes into the project being left first. UI thread. */
    static void opened(g2L p) {
        if (p == project) return;
        if (pending > 0 && project != null) { pending = 0; write(); }
        project = p;
        String c = comment(p);
        lastSeen = c;
        State.fromProject(blob(c));
        if (BitwigGroove.needsChange(p)) changed(); // e.g. a project saved before Shuffle/Accent were set to 0
    }

    /** The settings changed: write them into the project shortly (one undo step for a burst of changes). */
    static void changed() {
        if (project == null) return;
        int mine = ++pending;
        Thread t = new Thread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException e) { return; }
            if (mine != pending) return; // a later change will write
            try { CHR.Grk().asyncExec(() -> { if (pending == mine) { pending = 0; write(); } }); } catch (Throwable ignored) {}
        }, "cimm-groove-store");
        t.setDaemon(true);
        t.start();
    }

    private static void write() {
        g2L p = project;
        if (p == null) return;
        try {
            String c = comment(p);
            boolean values = BitwigGroove.needsChange(p);
            com.bitwig.flt.document.core.master.cXR m = master(p);
            if (m == null) { State.MOD.log("no master track to keep the groove in"); return; }
            if (!values) {
                String text = userText(c) + MARK + Base64.getEncoder().encodeToString(State.dumpProject().getBytes(StandardCharsets.UTF_8));
                if (text.equals(c)) return;
            }
            p.cfM("Change Groove", false); // one undo step: Bitwig's Shuffle/Accent and the settings that keep them
            String text;
            try {
                if (values) BitwigGroove.apply(p);
                text = userText(c) + MARK + Base64.getEncoder().encodeToString(State.dumpProject().getBytes(StandardCharsets.UTF_8));
                if (!text.equals(c)) m.TtY(text);
            } finally { p.cfM(false); }
            lastSeen = text;
        } catch (Throwable t) { State.MOD.log("couldn't store the groove in the project", t); }
    }

    /** Called twice a second: the block changed from outside (Bitwig's Undo / Redo): follow it. UI thread. */
    static void check() {
        g2L p = project;
        if (p == null || pending > 0 && false) return;
        String c = comment(p);
        if (c.equals(lastSeen)) return;
        lastSeen = c;
        State.fromProject(blob(c));
        State.MOD.log("groove settings follow the project (undo / redo)");
    }

    /** Bitwig's own Undo / Redo (for the Undo / Redo buttons). */
    static void undo() { try { if (project != null) project.Mb2(); } catch (Throwable t) { State.MOD.log("undo failed", t); } }
    static void redo() { try { if (project != null) project.LIK(); } catch (Throwable t) { State.MOD.log("redo failed", t); } }
}
