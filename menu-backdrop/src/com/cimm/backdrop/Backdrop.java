package com.cimm.backdrop;

import bitwig.obf.iok; // a colour
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Interface: Menu Backdrop.
 *
 * Bitwig's menus are windows of their own (dsQ); dsQ.t_(visible) shows and hides them, and the popup host opens each one
 * with its content (dnf.cfM(content, placement, focus)): menus are dKW, hover popups and tooltips are other kinds and are
 * ignored. While a menu is open, a dim layer (Bitwig's own popup overlay colour, "Popup overlay background color") sits
 * on each main window's layer stack above everything, fading in and out. The fade's frames are asked for from Bitwig's
 * UI timer (repaints asked for during a paint are dropped). All hooks live in this class (one helper class per mod).
 */
public final class Backdrop {
    private Backdrop() {}

    static final Bml.Mod MOD = Bml.mod("menu-backdrop");
    static final bml.api.Settings STORE = MOD.settings();

    static final String[] STYLES = {"Dim", "Off"};
    static final int DIM = 0, OFF = 1;
    /** The button that opened the menu stays above the dim (setting). */
    static volatile boolean raiseButton = STORE.getBoolean("raise_button", true);
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
    public static void popupOpening(@bml.api.Arg(0) Object content, @bml.api.Arg(1) Object placement) {
        opening.set(content);
        openingAnchor.set(content instanceof bitwig.obf.dKW ? anchorOf(placement) : null);
    }

    // ------------------------------------------------------------------------------------------- the menu's button

    /*
     * A menu opened from a button (File, Play... in the toolbar) is placed against that button: the placement (a dsP,
     * e.g. dPi) holds it in a widget field. That button is painted again on top of the dim, so the clicked menu name
     * stays in the foreground in its highlight colour while everything else dims.
     */
    private static final ThreadLocal<Object> openingAnchor = new ThreadLocal<>();
    /** Open menu popup -> the button it opened from. */
    static final java.util.Map<Object, bitwig.obf.dve> anchors = Collections.synchronizedMap(new WeakHashMap<>());

    private static bitwig.obf.dve anchorOf(Object placement) {
        if (placement == null) return null;
        try {
            for (Class<?> c = placement.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !bitwig.obf.dve.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object w = f.get(placement);
                    // a button: not a whole panel or track row (context menus are placed against those too)
                    if (w instanceof bitwig.obf.dzq b && b.yiX() <= 80 && b.Sxo() <= 400) return b;
                }
        } catch (Throwable t) { log("menu button not found", t); }
        return null;
    }

    /** The buttons of the menus open now, to paint over the dim. */
    static java.util.List<bitwig.obf.dve> openAnchors() {
        java.util.List<bitwig.obf.dve> out = new java.util.ArrayList<>();
        Object[] ps;
        synchronized (open) { ps = open.toArray(); }
        for (Object p : ps) { bitwig.obf.dve a = anchors.get(p); if (a != null) out.add(a); }
        return out;
    }

    @Hook(id = "popup-visible", target = bitwig.obf.dsQ.class, method = "t_(Z)V", at = At.ENTRY)
    public static void popupVisible(@bml.api.This Object popup, @bml.api.Arg(0) boolean visible) {
        try {
            if (visible) {
                Object content = opening.get();
                if (content != null) {
                    opening.set(null);
                    if (content instanceof bitwig.obf.dKW) menus.add(popup); else menus.remove(popup);
                    bitwig.obf.dve a = (bitwig.obf.dve) openingAnchor.get();
                    openingAnchor.set(null);
                    if (a != null && raiseButton) anchors.put(popup, a); else anchors.remove(popup);
                }
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
        if (any && style != OFF) addLayers();
        startTicker();
    }


    /** Repaints the windows about 60 times a second until the fade is done (from Bitwig's timer, on its UI thread). */
    private static java.util.TimerTask ticker;
    private static synchronized void startTicker() {
        if (ticker != null) return;
        try {
            ticker = bitwig.obf.dpN.TVZ().cfM(Backdrop::tick, 0, 16);
        } catch (Throwable t) { log("fade timer unavailable", t); }
    }
    /** One frame: forget closed menus, repaint the dim layers while fading, take them away once faded out. */
    private static void tick() {
        try { prune(); } catch (Throwable ignored) {}
        double a = level();
        Object[] ls;
        synchronized (layers) { ls = layers.values().toArray(); }
        for (Object l : ls) try { ((bitwig.obf.dve) l).jbi(); } catch (Throwable ignored) {}
        if (!shown && a <= 0) { removeLayers(); stopTicker(); }
    }

    private static synchronized void stopTicker() {
        if (ticker != null) { try { ticker.cancel(); } catch (Throwable ignored) {} ticker = null; }
    }

    // ------------------------------------------------------------------------------------------- windows

    /** Notes the main windows as they paint (the dim layer goes on each); popups are windows too and are skipped. */
    @Hook(id = "window-paint", target = bitwig.obf.duG.class, method = "MRY(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY)
    public static void windowPaint(@bml.api.This Object window) {
        if (!(window instanceof bitwig.obf.dsR)) windows.add(window);
    }

    // ------------------------------------------------------------------------------------------- the dim layer

    /*
     * The dim is a layer of its own on each window's layer stack (duG.sc(), the stack Bitwig puts its own in-window
     * popups on, above the main content), sized to the whole stack (dqt.layout). Everything in the window, meters
     * included, is under it. It takes no mouse events (DimLayer), and is taken off once the fade-out is over.
     */
    private static final java.util.Map<Object, Object> layers = Collections.synchronizedMap(new WeakHashMap<>());

    private static void addLayers() {
        Object[] ws;
        synchronized (windows) { ws = windows.toArray(); }
        for (Object o : ws) {
            if (!(o instanceof bitwig.obf.duG w) || layers.containsKey(w)) continue;
            try {
                bitwig.obf.dqs stack = w.sc();
                if (stack == null) continue;
                DimLayer layer = new DimLayer(w);
                stack.cfM(layer, l -> l.cfM(0, 0, stack.Sxo(), stack.yiX()));
                layers.put(w, layer);
            } catch (Throwable t) { log("dim layer failed", t); }
        }
    }

    private static void removeLayers() {
        Object[][] es;
        synchronized (layers) {
            es = layers.entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toArray(Object[][]::new);
            layers.clear();
        }
        for (Object[] e : es) {
            try { ((bitwig.obf.duG) e[0]).sc().MJ((bitwig.obf.dve) e[1]); } catch (Throwable t) { log("removing the dim layer failed", t); }
        }
    }

    /** The dim's colour now: the popup overlay colour at the fade's level (eased); null when nothing shows. */
    static iok dimColour(bitwig.obf.duG w) {
        double a = level();
        if (style == OFF || a <= 0) return null;
        double e = 1 - Math.pow(1 - a, 3);
        iok c = overlayColour(w);
        return new iok(c.FzB(), c.IwV(), c.UNj(), (float) (c.hUR() * e));
    }

    /** Bitwig's own colour for what lies behind a popup ("Popup overlay background color", themeable). */
    static iok overlayColour(bitwig.obf.duG w) {
        try {
            iok c = w.PEn().MLK().cfM(w);
            if (c != null) return c;
        } catch (Throwable t) { log("popup overlay colour unavailable; using black 80%", t); }
        return new iok(0.03f, 0.03f, 0.03f, 0.8f);
    }

    // ------------------------------------------------------------------------------------------- popups fade in

    /*
     * Popups are native windows: CocoaWindowHandle.Hnt(visible) shows one. On entry, for a popup window (Bitwig's
     * PopupWindowHandle) its native window (IBe.Hnt()) is made see-through, then raised to fully opaque over FADE_MS
     * from Bitwig's UI timer (NativeAlpha: [NSWindow setAlphaValue:] or [layer setOpacity:]).
     */
    static volatile boolean fadePopups = STORE.getBoolean("fade_popups", true);
    private static final double FADE_MS = 130;
    private static final java.util.Map<Long, Long> fading = new java.util.concurrent.ConcurrentHashMap<>();
    private static int fadeLogs;

    /** A window about to be shown or hidden (CocoaWindowHandle.Hnt(visible)): a popup goes see-through, then fades up. */
    @Hook(id = "window-showing", target = com.bitwig.mac_windowing_system.CocoaWindowHandle.class, method = "Hnt(Z)V", at = At.ENTRY)
    public static void windowShowing(@bml.api.This Object handle, @bml.api.Arg(0) boolean visible) {
        if (!visible || !fadePopups || !(handle instanceof com.bitwig.windowing_system.PopupWindowHandle popup)) return;
        try {
            long window = popup.Hnt(); // its native window
            if (window == 0 || !NativeAlpha.set(window, 0)) return;
            fading.put(window, System.nanoTime());
            if (fadeLogs < 3) { fadeLogs++; MOD.log("popup fading in (" + NativeAlpha.classOf(window) + ")"); }
            startFadeTicker();
        } catch (Throwable t) { log("popup fade failed", t); }
    }

    private static java.util.TimerTask fadeTicker;
    private static synchronized void startFadeTicker() {
        if (fadeTicker != null) return;
        try {
            fadeTicker = bitwig.obf.dpN.TVZ().cfM(Backdrop::fadeStep, 0, 16);
        } catch (Throwable t) {
            log("fade timer unavailable", t);
            for (Long w : fading.keySet()) NativeAlpha.set(w, 1);
            fading.clear();
        }
    }

    private static void fadeStep() {
        long now = System.nanoTime();
        for (java.util.Map.Entry<Long, Long> e : fading.entrySet()) {
            double t = Math.min(1, (now - e.getValue()) / 1e6 / FADE_MS);
            double a = 1 - Math.pow(1 - t, 3); // ease out
            NativeAlpha.set(e.getKey(), t >= 1 ? 1 : a);
            if (t >= 1) fading.remove(e.getKey());
        }
        if (fading.isEmpty()) synchronized (Backdrop.class) { if (fadeTicker != null) { fadeTicker.cancel(); fadeTicker = null; } }
    }

}
