package com.cimm.backdrop;

import bitwig.obf.iok; // a colour
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.graphics.SYo; // graphics
import com.bitwig.graphics.qc1; // an offscreen bitmap

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Interface: Menu Backdrop.
 *
 * Bitwig's menus and popups are windows of their own (dsQ); dsQ.t_(visible) shows and hides them. The main windows (duG)
 * paint their content in duG.MRY(g) (super.MRY(g) draws the widgets), then overlays. Right after the content (at the
 * call to dpN.TVZ() that follows it) this mod draws the backdrop over the whole window while any popup is shown:
 * - Dim: Bitwig's own popup overlay colour ("Popup overlay background color").
 * - Blur: when the menu opens, the window is painted once into a bitmap an eighth of its size (made by the window's
 *   own graphics, as Bitwig does for widgets drawn with transparency) and blurred with Bitwig's own 3x3 box filter
 *   (qc1.cfM(src, kernel, ...), as it does for icon shadows); that bitmap is drawn scaled up over the window (scaling it
 *   up softens it further), under half of the popup overlay colour.
 * Both fade in and out; while fading, a timer on Bitwig's UI thread asks the windows to repaint every frame. All hooks live in this class (BML takes
 * one helper class per mod).
 */
public final class Backdrop {
    private Backdrop() {}

    static final Bml.Mod MOD = Bml.mod("menu-backdrop");
    static final bml.api.Settings STORE = MOD.settings();

    static final String[] STYLES = {"Blur", "Dim", "Off"};
    static final int BLUR = 0, DIM = 1, OFF = 2;
    static volatile int style = Math.max(0, Math.min(OFF, STORE.getInt("style", BLUR)));

    private static final double IN_MS = 180, OUT_MS = 150;
    private static final int DOWN = 8, PASSES = 3;
    private static final float[][] BOX = {{1 / 9f, 1 / 9f, 1 / 9f}, {1 / 9f, 1 / 9f, 1 / 9f}, {1 / 9f, 1 / 9f, 1 / 9f}};

    /** Popups shown now. */
    private static final Set<Object> open = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Main windows seen painting (to repaint them when a popup opens or closes, and while fading). */
    private static final Set<Object> windows = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Each window's blurred picture, taken when the backdrop starts from nothing. */
    private static final Map<Object, Snap> snaps = Collections.synchronizedMap(new WeakHashMap<>());

    private record Snap(qc1 bitmap, int w, int h, java.util.List<qc1> all) {}

    // fade state: the level (0..1) at the last change, when, and the direction
    private static volatile boolean shown;
    private static volatile double level0;
    private static volatile long changed;

    /** Set once the window's graphics turn out not to be a picture that can be copied (Bitwig records drawing
     *  commands instead): Blur then dims without trying again. */
    private static volatile boolean noBlur;
    private static int logs;
    private static void log(String s) { if (logs < 30) { logs++; MOD.log(s); } }
    private static void log(String s, Throwable t) { if (logs < 30) { logs++; MOD.log(s, t); } }

    static double level() {
        double t = System.nanoTime() / 1e6 - changed / 1e6;
        return shown ? Math.min(1, level0 + t / IN_MS) : Math.max(0, level0 - t / OUT_MS);
    }

    // ------------------------------------------------------------------------------------------- popups

    /** Popup windows that hold a menu (only these dim the window: hover popups and tooltips don't). */
    private static final Set<Object> menus = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** What the popup host is opening right now (dnf.cfM(content, placement, focus) creates the window and shows it). */
    private static final ThreadLocal<Object> opening = new ThreadLocal<>();
    private static int contentLogs;

    @Hook(id = "popup-opening", target = bitwig.obf.dnf.class, method = "cfM(LdrO;LdsP;Z)V", at = At.ENTRY)
    public static void popupOpening(@bml.api.Arg(0) Object content) {
        opening.set(content);
        if (contentLogs < 15) { contentLogs++; MOD.log("popup content: " + (content == null ? "none" : content.getClass().getName())
                + (content instanceof bitwig.obf.dKW ? " (a menu: dims)" : " (not a menu)")); }
    }

    @Hook(id = "popup-visible", target = bitwig.obf.dsQ.class, method = "t_(Z)V", at = At.ENTRY)
    public static void popupVisible(@bml.api.This Object popup, @bml.api.Arg(0) boolean visible) {
        try {
            if (visible) {
                Object content = opening.get();
                if (content != null) { opening.set(null); if (content instanceof bitwig.obf.dKW) menus.add(popup); else menus.remove(popup); }
                if (!menus.contains(popup)) return; // a hover popup or tooltip
            }
            if (visible) open.add(popup); else open.remove(popup);
            update(visible ? "shown" : "hidden");
        } catch (Throwable t) { log("popup tracking failed", t); }
    }

    /** A popup thrown away (choosing a menu item can close the menu this way, without hiding it first). */
    @Hook(id = "popup-disposed", target = bitwig.obf.dsQ.class, method = "ix_()V", at = At.ENTRY)
    public static void popupDisposed(@bml.api.This Object popup) {
        try { if (open.remove(popup)) update("disposed"); } catch (Throwable t) { log("popup tracking failed", t); }
    }

    /** Forgets popups that are no longer on screen, whatever closed them. */
    private static void prune() {
        if (open.isEmpty()) return;
        boolean removed;
        synchronized (open) {
            removed = open.removeIf(p -> { try { return !((bitwig.obf.dvW) p).uev(); } catch (Throwable t) { return true; } });
        }
        if (removed) update("gone");
    }

    /** Starts the fade in or out when popups appear or all are gone. */
    private static synchronized void update(String why) {
        boolean any = !open.isEmpty();
        if (any == shown) return;
        double now = level();
        level0 = now; changed = System.nanoTime(); shown = any;
        if (any && now <= 0) snaps.clear(); // a new picture for a backdrop that starts from nothing
        if (logs < 20) log("popup " + why + "; backdrop " + (any ? "in" : "out"));
        repaintWindows();
        startTicker();
    }

    /**
     * Drives the fade: repaints the windows about 60 times a second until the fade is done, then once more. Bitwig
     * ignores repaint requests made while a window is painting, so the frames are asked for from its timer (whose tasks
     * run on the UI thread), never from inside the paint.
     */
    private static java.util.TimerTask ticker;
    private static synchronized void startTicker() {
        if (ticker != null) return;
        try {
            ticker = bitwig.obf.dpN.TVZ().cfM(() -> {
                repaintWindows();
                double a = level();
                if (shown ? a >= 1 : a <= 0) stopTicker();
            }, 16, 16);
        } catch (Throwable t) { log("fade timer unavailable", t); }
    }
    private static synchronized void stopTicker() {
        if (ticker != null) { try { ticker.cancel(); } catch (Throwable ignored) {} ticker = null; }
    }

    private static void repaintWindows() {
        Object[] ws;
        synchronized (windows) { ws = windows.toArray(); }
        for (Object w : ws) try { ((bitwig.obf.dve) w).jbi(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------------------------------- painting

    private static final ThreadLocal<Object> painting = new ThreadLocal<>();
    private static final ThreadLocal<SYo> graphics = new ThreadLocal<>();

    @Hook(id = "window-paint", target = bitwig.obf.duG.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void windowPaint(@bml.api.This Object window, @bml.api.Arg(0) SYo g) {
        if (window instanceof bitwig.obf.dsR || capturing.get() != null) { painting.set(null); graphics.set(null); return; } // a popup, or the blur's own paint
        painting.set(window); graphics.set(g);
        windows.add(window);
    }

    /** Called right after the window's content is drawn (it replaces the dpN.TVZ() that follows). */
    @Hook(id = "after-content", target = bitwig.obf.duG.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.dpN.class, call = "TVZ()LdpN;")
    public static bitwig.obf.dpN afterContent() {
        Object w = painting.get();
        SYo g = graphics.get();
        painting.set(null); graphics.set(null);
        if (w != null && g != null) {
            try { prune(); } catch (Throwable ignored) {}
            try { backdrop((bitwig.obf.duG) w, g); } catch (Throwable t) { log("backdrop failed", t); }
        }
        return bitwig.obf.dpN.TVZ();
    }

    private static void backdrop(bitwig.obf.duG w, SYo g) {
        int st = style;
        double a = level();
        if (st == OFF || a <= 0) {
            if (!shown) release(w);
            return;
        }
        double e = 1 - Math.pow(1 - a, 3); // ease out
        double W = w.Sxo(), H = w.yiX();
        iok overlay = overlayColour(w);
        boolean blurred = false;
        if (st == BLUR && !noBlur) {
            Snap s = snaps.get(w);
            if (s == null && !snaps.containsKey(w)) { s = snapshot(w, g); snaps.put(w, s); } // null = couldn't
            if (s != null) {
                g.qoU();
                try {
                    g.Hnt(W / s.w(), H / s.h());
                    g.cfM(s.bitmap(), 0, 0, e);
                } finally { g.ucQ(); }
                blurred = true;
            }
        }
        g.Hnt(new iok(overlay.FzB(), overlay.IwV(), overlay.UNj(), (float) (overlay.hUR() * e * (blurred ? 0.5 : 1))));
        g.MRY(0, 0, W, H);
        g.wGc();
    }

    /** Bitwig's own colour for what lies behind a popup ("Popup overlay background color", themeable). */
    private static iok overlayColour(bitwig.obf.duG w) {
        try {
            iok c = w.PEn().MLK().cfM(w);
            if (c != null) return c;
        } catch (Throwable t) { log("popup overlay colour unavailable; using black 80%", t); }
        return new iok(0.03f, 0.03f, 0.03f, 0.8f);
    }

    /** True while the window is being painted into the blur bitmap (the backdrop hooks stay out of that paint). */
    private static final ThreadLocal<Boolean> capturing = new ThreadLocal<>();

    /**
     * The window's current picture, an eighth of its size, blurred; null if it can't be made here. Done the way Bitwig
     * paints a widget with transparency (dve.cfM(g, alpha)): a bitmap made by the window's own graphics (g.cfM(w, h,
     * format), so it works with Bitwig's recorded drawing), the window painted into it (scaled down), then Bitwig's 3x3
     * box filter a few times. The bitmaps are kept until the backdrop is gone (the drawing runs later, from the record).
     */
    private static Snap snapshot(bitwig.obf.duG w, SYo g) {
        java.util.List<qc1> made = new java.util.ArrayList<>();
        try {
            double W = w.Sxo(), H = w.yiX();
            double k = 1;
            try { k = g.Hnt(); } catch (Throwable ignored) {}
            int sw = (int) Math.max(1, Math.ceil(W * k / DOWN)), sh = (int) Math.max(1, Math.ceil(H * k / DOWN));
            qc1 a = g.cfM(sw, sh, com.bitwig.graphics.Jkg.cfM);
            made.add(a);
            SYo gs = a.cfM();
            capturing.set(Boolean.TRUE);
            try {
                gs.Hnt(sw / W, sh / H);
                w.Hnt(gs); // the window and everything in it
            } finally {
                capturing.set(null);
                gs.ix_();
            }
            for (int i = 0; i < PASSES; i++) {
                qc1 b = g.cfM(sw, sh, com.bitwig.graphics.Jkg.cfM);
                made.add(b);
                b.cfM(a, BOX, com.bitwig.graphics.zLm.cfM, 1, 1, 1f);
                a = b;
            }
            log("blur made: window " + Math.round(W) + "x" + Math.round(H) + " at scale " + k + " -> " + sw + "x" + sh
                    + " (" + g.getClass().getSimpleName() + ")");
            return new Snap(a, sw, sh, made);
        } catch (Throwable t) {
            noBlur = true;
            for (qc1 b : made) dispose(b);
            log("blur failed; dimming instead", t);
            return null;
        }
    }

    private static void release(Object w) {
        Snap s = snaps.remove(w);
        if (s != null) for (qc1 b : s.all()) dispose(b);
    }

    private static void dispose(qc1 b) {
        try { b.ix_(); } catch (Throwable ignored) {}
    }
}
