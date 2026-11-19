package com.cimm.mixerminipanels;

import bitwig.obf.jsd; // a device
import bml.api.Bml;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Devices hidden from the mixer's Devices section (Hide in Mixer / Show in Mixer in a device's right-click menu).
 * Hiding only removes the device's row in the mixer: the device stays in its chain, processing, and in the device panel.
 *
 * A device is remembered as "track UUID | device name | which one of that name in its chain" — track UUIDs are random
 * and unique across projects, so the list lives in the mod's settings (per install) and survives saving, reopening and
 * reordering tracks. Renaming the device shows it again. Devices whose channel has no track UUID (inside a layer)
 * are remembered for this session only.
 */
final class Hidden {
    private static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    private static final String KEY = "hidden_devices";
    private Hidden() {}

    private static Set<String> keys;                                                     // saved hidden devices
    private static final Set<jsd> session = Collections.newSetFromMap(new WeakHashMap<>()); // no stable key
    /** The mixer rows built for each device (several: mixer, Inspector, rebuilt rows), to hide or show them at once. */
    private static final Map<jsd, List<bitwig.obf.dve>> rows = new WeakHashMap<>();

    private static synchronized Set<String> keys() {
        if (keys == null) {
            keys = new LinkedHashSet<>();
            String s = MOD.settings().get(KEY, "");
            for (String k : s.split("\n")) if (!k.isBlank()) keys.add(k);
        }
        return keys;
    }

    private static synchronized void save() { MOD.settings().put(KEY, String.join("\n", keys())); }

    /** "trackUUID|name|n", or null if the device's channel has no track UUID. */
    static String key(jsd device) {
        try {
            Object ch = device.wYK();                       // the channel it is on
            if (!(ch instanceof bitwig.obf.xJo track)) return null;
            java.util.UUID id = track.kFU();
            if (id == null) return null;
            String name = Params.safeName(device);
            int n = 0;
            bitwig.obf.FAu chain = device.xL();             // its device chain
            if (chain != null) {
                int at = chain.cfM(device);                 // its position
                for (int i = 0; i < at; i++) {
                    jsd d = chain.MRY(i);
                    if (d != null && name.equals(Params.safeName(d))) n++;
                }
            }
            return id + "|" + name + "|" + n;
        } catch (Throwable t) { return null; }
    }

    static boolean isHidden(Object device) {
        if (!(device instanceof jsd d)) return false;
        synchronized (Hidden.class) {
            if (session.contains(d)) return true;
            String k = key(d);
            return k != null && keys().contains(k);
        }
    }

    static void set(Object device, boolean hide) {
        if (!(device instanceof jsd d)) return;
        synchronized (Hidden.class) {
            String k = key(d);
            if (k == null) { if (hide) session.add(d); else session.remove(d); }
            else { if (hide) keys().add(k); else keys().remove(k); save(); }
        }
        List<bitwig.obf.dve> rs;
        synchronized (rows) { rs = rows.get(d) == null ? List.of() : new java.util.ArrayList<>(rows.get(d)); }
        for (bitwig.obf.dve r : rs) show(r, !hide);
        MOD.log((hide ? "hidden in mixer: " : "shown in mixer: ") + Params.safeName(d));
    }

    /** A row the mixer just built for the device: remembered, and hidden if the device is hidden. */
    static void row(jsd device, bitwig.obf.dve row) {
        if (device == null || row == null) return;
        synchronized (rows) {
            List<bitwig.obf.dve> l = rows.computeIfAbsent(device, k -> new java.util.ArrayList<>());
            l.removeIf(r -> r == null);
            if (l.size() > 16) l.remove(0); // old rebuilt rows
            l.add(row);
        }
        if (isHidden(device)) show(row, false);
    }

    private static void show(bitwig.obf.dve row, boolean visible) {
        try { row.t_(visible); } catch (Throwable ignored) {}
        try { row.kJ_(); } catch (Throwable ignored) {} // tell the parents the size changed
    }
}
