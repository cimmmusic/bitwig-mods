package com.cimm.backdrop;

import bitwig.obf.iok; // a colour
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.graphics.SYo; // graphics

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Interface: Menu Backdrop.
 *
 * Bitwig's menus are windows of their own (dsQ); dsQ.t_(visible) shows and hides them, and the popup host opens each one
 * with its content (dnf.cfM(content, placement, focus)): menus are dKW, hover popups and tooltips are other kinds and are
 * ignored. The main windows (duG) paint their content in duG.MRY(g), then overlays. Right after the content (at the call
 * to dpN.TVZ() that follows it) this mod draws Bitwig's own popup overlay colour ("Popup overlay background color") over
 * the whole window while a menu is open, fading in and out. The fade's frames are asked for from Bitwig's UI timer
 * (repaints asked for during a paint are dropped). All hooks live in this class (BML takes one helper class per mod).
 */
public final class Backdrop {
    private Backdrop() {}

    static final Bml.Mod MOD = Bml.mod("menu-backdrop");
    static final bml.api.Settings STORE = MOD.settings();

    static final String[] STYLES = {"Dim", "Off"};
    static final int DIM = 0, OFF = 1;
    static volatile int style = Math.max(0, Math.min(OFF, STORE.getInt("style", DIM)));

    private static final double IN_MS = 180, OUT_MS = 150;

    /** Menus shown now. */
    private static final Set<Object> open = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Main windows seen painting (to repaint them when a menu opens or closes, and while fading). */
    private static final Set<Object> windows = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    // fade state: the level (0..1) at the last change, when, and the direction
    private static volatile boolean shown;
    private static volatile double level0;
    private static volatile long changed;

    private static int logs;
    private static void log(String s) { if (logs < 30) { logs++; MOD.log(s); } }
    private static void log(String s, Throwable t) { if (logs < 30) { logs++; MOD.log(s, t); } }

    static double level() {
        double t = (System.nanoTime() - changed) / 1e6;
        return shown ? Math.min(1, level0 + t / IN_MS) : Math.max(0, level0 - t / OUT_MS);
    }

    // ------------------------------------------------------------------------------------------- menus

    /** Popup windows that hold a menu (only these dim the window: hover popups and tooltips don't). */
    private static final Set<Object> menus = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** What the popup host is opening right now (dnf.cfM(content, placement, focus) creates the window and shows it). */
    private static final ThreadLocal<Object> opening = new ThreadLocal<>();

    @Hook(id = "popup-opening", target = bitwig.obf.dnf.class, method = "cfM(LdrO;LdsP;Z)V", at = At.ENTRY)
    public static void popupOpening(@bml.api.Arg(0) Object content) {
        opening.set(content);
    }

    @Hook(id = "popup-visible", target = bitwig.obf.dsQ.class, method = "t_(Z)V", at = At.ENTRY)
    public static void popupVisible(@bml.api.This Object popup, @bml.api.Arg(0) boolean visible) {
        try {
            if (visible) {
                Object content = opening.get();
                if (content != null) { opening.set(null); if (content instanceof bitwig.obf.dKW) menus.add(popup); else menus.remove(popup); }
                if (!menus.contains(popup)) return; // a hover popup or tooltip
                open.add(popup);
            } else open.remove(popup);
            update(visible ? "shown" : "hidden");
        } catch (Throwable t) { log("popup tracking failed", t); }
    }

    /** A popup thrown away (choosing a menu item can close the menu this way, without hiding it first). */
    @Hook(id = "popup-disposed", target = bitwig.obf.dsQ.class, method = "ix_()V", at = At.ENTRY)
    public static void popupDisposed(@bml.api.This Object popup) {
        try { if (open.remove(popup)) update("disposed"); } catch (Throwable t) { log("popup tracking failed", t); }
    }

    /** Forgets menus that are no longer on screen, whatever closed them. */
    private static void prune() {
        if (open.isEmpty()) return;
        boolean removed;
        synchronized (open) {
            removed = open.removeIf(p -> { try { return !((bitwig.obf.dvW) p).uev(); } catch (Throwable t) { return true; } });
        }
        if (removed) update("gone");
    }

    /** Starts the fade in or out when menus appear or all are gone. */
    private static synchronized void update(String why) {
        boolean any = !open.isEmpty();
        if (any == shown) return;
        double now = level();
        level0 = now; changed = System.nanoTime(); shown = any;
        if (logs < 10) log("menu " + why + "; backdrop " + (any ? "in" : "out"));
        repaintWindows();
        startTicker();
    }

    private static void repaintWindows() {
        Object[] ws;
        synchronized (windows) { ws = windows.toArray(); }
        for (Object w : ws) try { ((bitwig.obf.dve) w).jbi(); } catch (Throwable ignored) {}
    }

    /** Repaints the windows about 60 times a second until the fade is done (from Bitwig's timer, on its UI thread). */
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

    // ------------------------------------------------------------------------------------------- painting

    private static final ThreadLocal<Object> painting = new ThreadLocal<>();
    private static final ThreadLocal<SYo> graphics = new ThreadLocal<>();

    @Hook(id = "window-paint", target = bitwig.obf.duG.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void windowPaint(@bml.api.This Object window, @bml.api.Arg(0) SYo g) {
        if (window instanceof bitwig.obf.dsR) { painting.set(null); graphics.set(null); return; } // a popup itself
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
            try { dim((bitwig.obf.duG) w, g); } catch (Throwable t) { log("backdrop failed", t); }
        }
        return bitwig.obf.dpN.TVZ();
    }

    private static void dim(bitwig.obf.duG w, SYo g) {
        double a = level();
        if (style == OFF || a <= 0) return;
        double e = 1 - Math.pow(1 - a, 3); // ease out
        iok c = overlayColour(w);
        g.Hnt(new iok(c.FzB(), c.IwV(), c.UNj(), (float) (c.hUR() * e)));
        g.MRY(0, 0, w.Sxo(), w.yiX());
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
}
