package com.cimm.uifont;

/** How far the user's fonts are moved down, and which loaded faces are the user's. */
final class Shift {
    private Shift() {}

    /** Settings key: pixels to move the user's fonts down (negative moves up). */
    static final String KEY = "move_down";
    static final double DEFAULT = 2;

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
}
