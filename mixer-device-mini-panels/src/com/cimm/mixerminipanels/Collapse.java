package com.cimm.mixerminipanels;

import bitwig.obf.jsd; // a device
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which devices' mini views are collapsed (this session), separately for the mixer and for the track Inspector,
 * and the live views to update when that changes. Mini views start expanded in the mixer and collapsed in the
 * Inspector (the Inspector stacks every device, so it gets busy).
 */
final class Collapse {
    static final int MIXER = 0, INSPECTOR = 1;
    private static final boolean[] DEFAULT = {false, true}; // collapsed by default: mixer no, Inspector yes

    private static final Map<jsd, boolean[]> state = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MiniGraph, Boolean> live = Collections.synchronizedMap(new WeakHashMap<>());
    /** Bitwig's own panels shown as mini views (Oscilloscope, Spectrum): their device and place. */
    private static final Map<bitwig.obf.dve, Object[]> nativeLive = Collections.synchronizedMap(new WeakHashMap<>());
    private Collapse() {}

    static String placeName(int place) { return place == INSPECTOR ? "Inspector" : "Mixer"; }

    static boolean isCollapsed(Object d, int place) {
        boolean[] s = d == null ? null : state.get(d);
        return s == null ? DEFAULT[place] : s[place];
    }

    static void track(MiniGraph g) { live.put(g, Boolean.TRUE); }
    static List<MiniGraph> liveViews() { synchronized (live) { return new ArrayList<>(live.keySet()); } }

    /** Track a native panel mini view; hidden right away if its device is collapsed in that place. */
    static void trackNative(bitwig.obf.dve view, jsd device, int place) {
        nativeLive.put(view, new Object[] {device, place});
        if (isCollapsed(device, place)) show(view, false);
    }

    private static void show(bitwig.obf.dve view, boolean visible) {
        try { view.t_(visible); } catch (Throwable ignored) {}
        try { view.kJ_(); } catch (Throwable ignored) {} // tell the parents our size changed
    }

    static boolean hasMiniView(Object device) {
        if (!(device instanceof jsd d)) return false;
        return Kinds.supported(d) || MixerMiniPanels.NATIVE_PANEL_VIEWS.contains(Params.safeName(d));
    }

    /** Collapse or expand the device's mini views in one place (mixer or Inspector) only. */
    static void set(Object device, int place, boolean c) {
        if (!(device instanceof jsd d)) return;
        boolean[] s = state.computeIfAbsent(d, k -> DEFAULT.clone());
        s[place] = c;
        List<MiniGraph> views;
        synchronized (live) { views = new ArrayList<>(live.keySet()); }
        for (MiniGraph g : views) if (g.device == d && g.place == place) g.applyCollapsed(c);
        List<Map.Entry<bitwig.obf.dve, Object[]>> natives;
        synchronized (nativeLive) { natives = new ArrayList<>(nativeLive.entrySet()); }
        for (Map.Entry<bitwig.obf.dve, Object[]> e : natives)
            if (e.getValue()[0] == d && (int) e.getValue()[1] == place) show(e.getKey(), !c);
    }
}
