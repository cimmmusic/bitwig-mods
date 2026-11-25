package com.cimm.minimal;

/** How far the user's fonts are moved down, and which loaded faces are the user's. */
final class Shift {
    private Shift() {}

    /** Settings key: pixels to move the user's fonts down (negative moves up). */
    static final String KEY = "move_down_px"; // new key (0.3.0-dev.6): earlier builds defaulted to 2, which clips Lato
    static final double DEFAULT = 1; // Minimal Dark: Lato sits 1 px high in Bitwig's fields

    private static volatile double px = read();
    private static volatile Object[] faces = new Object[0];
    static final ThreadLocal<com.bitwig.graphics.render_commands.hLr> current = new ThreadLocal<>();

    static double read() {
        try { return Double.parseDouble(Fonts.STORE.get(KEY, Double.toString(DEFAULT))); } catch (Throwable t) { return DEFAULT; }
    }
    static double px() { return px; }
    static void set(double v) { px = v; Fonts.STORE.put(KEY, Double.toString(v)); }

    /** Notes a face made from one of the user's font files. */
    static synchronized <T> T mark(T face) {
        if (face == null) return null;
        Object[] next = java.util.Arrays.copyOf(faces, faces.length + 1);
        next[faces.length] = face;
        faces = next;
        return face;
    }

    /** The offset for a run drawn with this face: px for the user's fonts, 0 for Bitwig's own. */
    static double offset(com.bitwig.graphics.render_commands.hLr run) {
        double d = px;
        if (d == 0 || run == null) return 0;
        Object face = run.MRY; // the run's face (the one the font cache loaded)
        for (Object f : faces) if (f == face) return d;
        return 0;
    }

    /** Whether the run's face is one of the user's fonts (not Bitwig's icon font or another fallback). */
    static boolean userFace(com.bitwig.graphics.render_commands.hLr run) {
        if (run == null) return false;
        Object face = run.MRY;
        for (Object f : faces) if (f == face) return true;
        return false;
    }

    private static int fitLogs;

    /**
     * How far a run with its baseline at y may move down: the wanted d, but no further than the room between the
     * bottom of its letters (baseline + the font's descent: the tails of g, y, p) and the bottom of the area Bitwig
     * clips the text to (its field or label). So text moves down where there is space and never gets cut off.
     */
    static double fit(com.bitwig.graphics.SYo g, com.bitwig.graphics.render_commands.hLr run, double y, double d) {
        try {
            com.bitwig.base.geom.DoubleRectangle clip = g.TtY();
            if (clip == null || !(clip.IwV > 0)) return d;
            double descent = run.cfM().MRY(); // the font's descent at this size
            double room = (clip.Hnt + clip.IwV) - (y + descent);
            double ok = Math.max(0, Math.min(d, room));
            if (fitLogs < 5 && ok < d) {
                fitLogs++;
                Fonts.MOD.log("text moved " + Math.round(ok * 10) / 10.0 + " of " + d + " (room " + Math.round(room * 10) / 10.0
                        + ", descent " + Math.round(descent * 10) / 10.0 + ", clip bottom " + Math.round((clip.Hnt + clip.IwV) * 10) / 10.0 + ")");
            }
            return ok;
        } catch (Throwable t) {
            if (fitLogs < 5) { fitLogs++; Fonts.MOD.log("text fit failed", t); }
            return d;
        }
    }
}
