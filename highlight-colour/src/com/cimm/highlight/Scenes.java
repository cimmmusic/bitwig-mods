package com.cimm.highlight;

import bitwig.obf.iok; // a colour (r, g, b, a floats: FzB, IwV, UNj, hUR)

/**
 * Dark scene backgrounds.
 *
 * Launcher slots and scenes (bdO) fill their background with their colour (bdQ.pYg(), from the scene's colour) in
 * bdO.a_(g): g.Hnt(colour) then a rounded fill. A scene without a colour of its own gets a neutral mid grey, which
 * stands out on a dark theme. For scene buttons (bdw: the arranger's and the mixer's scene headers), a neutral grey is
 * drawn much darker instead, keeping its relative brightness (so a lighter state stays a little lighter). Scenes the
 * user coloured keep their colour; clip slots are left alone.
 */
public final class Scenes {
    private Scenes() {}

    static final String KEY = "dark_scenes";
    static volatile boolean on = Highlight.STORE.getBoolean(KEY, true);

    /** The grey a scene's default colour becomes (#1E1E1E for Bitwig's #494949). */
    private static final float DARK = 0x1E / 255f, FROM = 0x49 / 255f;

    static final ThreadLocal<Boolean> scene = new ThreadLocal<>();
    static int logs;

    /** A neutral grey made dark; null for a real colour (or none). */
    static iok darker(iok c) {
        if (c == null || c.hUR() <= 0) return null;
        float r = c.FzB(), gr = c.IwV(), b = c.UNj();
        float max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
        if (max <= 0 || (max - min) / max > 0.12f) return null; // coloured: keep it
        float v = Math.min(1f, DARK * max / FROM);
        return new iok(v, v, v, c.hUR());
    }

    static String hex(iok c) {
        return String.format("#%02X%02X%02X", Math.round(c.FzB() * 255), Math.round(c.IwV() * 255), Math.round(c.UNj() * 255));
    }
}
