package com.cimm.mixerminipanels;

import bitwig.obf.gDs; // a parameter's value range
import bitwig.obf.hSh; // a settable device parameter (value, range, set with undo)
import bitwig.obf.jsd; // a device
import bitwig.obf.v3U; // a device parameter as the device lists it
import bml.api.Bml;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A device's parameters, found by ID and display name. */
final class Params {
    private static final Bml.Mod MOD = Bml.mod("mixer-device-mini-panels");
    private static final Set<String> logged = ConcurrentHashMap.newKeySet();

    record P(String id, String name, hSh param) {
        double value() { try { return param.iCS(); } catch (Throwable t) { return 0; } } // plain value
        double min() { try { return param.QNk().wGc(); } catch (Throwable t) { return 0; } }
        double max() { try { return param.QNk().qoU(); } catch (Throwable t) { return 1; } }
        double norm() { double a = min(), b = max(); return b > a ? (value() - a) / (b - a) : 0; }
        String key() { return (id + " " + name).toLowerCase(Locale.ROOT); }
    }

    final List<P> all = new ArrayList<>();
    final List<String> other = new ArrayList<>(); // parameters that aren't settable values (choices, switches): logged only
    final java.util.Map<String, Object> choices = new java.util.HashMap<>(); // those parameters by upper-case ID

    /** The displayed value of a choice/switch parameter (e.g. "Bell", "On", "±10"), read now; "" if unknown. */
    String text(String id) {
        Object p = choices.get(id.toUpperCase(Locale.ROOT));
        return p == null ? "" : name(p);
    }

    Params(jsd device) {
        String[] ids = null;
        try { ids = device.qm(); } catch (Throwable ignored) {} // the device's parameter IDs
        if (ids != null) {
            for (String id : ids) {
                try {
                    v3U p = device.MRY(id); // the parameter with that ID
                    if (p instanceof hSh h) all.add(new P(id, name(p), h)); // Bitwig's parameter objects are settable
                    else if (p != null) choices.put(id.toUpperCase(Locale.ROOT), p);
                    if (!(p instanceof hSh) && p != null) other.add(id + " | " + name(p) + " | " + p.getClass().getName() + " | aS_=" + Reflect.call(p, "aS_"));
                } catch (Throwable ignored) {}
            }
        }
        String dev = safeName(device);
        if (logged.add(dev)) {
            StringBuilder sb = new StringBuilder("PARAMS of '" + dev + "' (" + all.size() + "):\n");
            for (P p : all) sb.append("  ").append(p.id).append(" | ").append(p.name)
                    .append(" | v=").append(p.value()).append(" [").append(p.min()).append(", ").append(p.max()).append("]\n");
            for (String o : other) sb.append("  (other) ").append(o).append("\n");
            MOD.log(sb.toString());
        }
    }

    static String safeName(jsd device) {
        try { String n = device.aF_(); return n == null ? "" : n; } catch (Throwable t) { return ""; } // the device's display name
    }

    /** The parameter's displayed value text (Bitwig's getters are obfuscated, so try the String ones). */
    static String name(Object p) {
        for (String m : new String[] {"qFI", "fFs", "ax_", "au_", "BDm"}) {
            Object r = Reflect.call(p, m);
            if (r instanceof String s && !s.isBlank()) return s;
        }
        return "";
    }

    /** The first parameter whose ID or name contains every word (case-insensitive), or null. */
    P find(String... words) {
        outer:
        for (P p : all) {
            String k = p.key();
            for (String w : words) if (!k.contains(w)) continue outer;
            return p;
        }
        return null;
    }

    /** The first parameter whose ID or name contains any of the texts. */
    P findAny(String... texts) {
        for (String t : texts) { P p = find(t); if (p != null) return p; }
        return null;
    }

    /** For band n: a parameter with a word token accepted by {@code word} and the number n as its own token. */
    P band(java.util.function.Predicate<String> word, int n) {
        String num = Integer.toString(n);
        for (P p : all) {
            String k = p.key();
            boolean hasNum = false, hasWord = false;
            for (String tok : k.split("[^0-9]+")) if (tok.equals(num)) hasNum = true;
            for (String tok : k.split("[^a-z]+")) if (!tok.isEmpty() && word.test(tok)) hasWord = true;
            if (hasNum && hasWord) return p;
        }
        return null;
    }
}
