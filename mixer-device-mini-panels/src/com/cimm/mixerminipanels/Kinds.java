package com.cimm.mixerminipanels;

import bitwig.obf.jsd; // a device
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The devices that get a mini view; everything else keeps Bitwig's plain row.
 *
 * A device is recognised by its display name first ("EQ+", "Compressor", ...). A preset renames the device (the row
 * shows the preset's name), so otherwise it is recognised by its parameter set, which a preset doesn't change
 * (IDs confirmed in the mod log, Bitwig 6.1.4).
 */
final class Kinds {
    private Kinds() {}

    static final String EQ_PLUS = "EQ+", EQ5 = "EQ-5", EQ2 = "EQ-2", COMP = "Compressor", COMP_PLUS = "Compressor+", VU = "VU Meter", DYN = "Dynamics", SAT = "Saturator", TOOL = "Tool", FILTER = "Filter+", GATE = "Gate", LIMITER = "Peak Limiter", TRANSIENT = "Transient Control", PLAIN_FILTER = "Filter", OVER = "Over";
    private static final Set<String> NAMES = Set.of(EQ_PLUS, EQ5, EQ2, COMP, COMP_PLUS, VU, DYN, SAT, TOOL, FILTER, GATE, LIMITER, TRANSIENT, PLAIN_FILTER, OVER);
    private static final Map<jsd, String> cache = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    /** The kind of device this is (one of the names above), or null when it gets no mini view. */
    static String kind(jsd device) {
        if (device == null) return null;
        String name = Params.safeName(device);
        if (NAMES.contains(name)) return name;
        return cache.computeIfAbsent(device, Kinds::bySignature);
    }

    private static String bySignature(jsd device) {
        Set<String> ids = new java.util.HashSet<>();
        try { for (String id : device.qm()) ids.add(id.toUpperCase(java.util.Locale.ROOT)); } catch (Throwable ignored) {}
        String k = null;
        if (ids.contains("CONTENTS/NEEDLE_1")) k = VU;
        else if (ids.contains("CONTENTS/LO_FREQ") && ids.contains("CONTENTS/HI_FREQ")) k = EQ2;
        else if (ids.contains("CONTENTS/FREQ8")) k = EQ_PLUS;
        else if (ids.contains("CONTENTS/FREQ5") && !ids.contains("CONTENTS/FREQ6")) k = EQ5;
        else if (ids.contains("CONTENTS/LOW_THRESHOLD") && ids.contains("CONTENTS/DRIVE") && ids.contains("CONTENTS/SKEW_THRESHOLD")) k = SAT;
        else if (ids.contains("CONTENTS/LOW_THRESHOLD") && ids.contains("CONTENTS/HIGH_THRESHOLD") && !ids.contains("CONTENTS/DRIVE")) k = DYN;
        else if (ids.contains("CONTENTS/THRESHOLD") && ids.contains("CONTENTS/RATIO") && ids.contains("CONTENTS/SMOOTH")) k = COMP_PLUS;
        else if (ids.contains("CONTENTS/THRESHOLD") && ids.contains("CONTENTS/RATIO") && ids.contains("CONTENTS/INPUT")
                && !ids.contains("CONTENTS/SMOOTH")) k = COMP;
        if (k != null) if (MiniGraph.DEBUG) MiniGraph.MOD.log("recognised '" + Params.safeName(device) + "' as " + k + " (by its parameters)");
        return k;
    }

    static boolean supported(jsd device) { return kind(device) != null; }

    static MiniGraph create(jsd device) {
        String k = kind(device);
        if (k == null) return null;
        switch (k) {
            case EQ_PLUS: case EQ5: case EQ2: return new EqGraph(device);
            case COMP_PLUS: return new CompGraph(device);
            case COMP: return new LegacyCompGraph(device);
            case VU: return new VuGraph(device);
            case DYN: return new DynGraph(device);
            case SAT: return new SatGraph(device);
            case TOOL: return new ToolGraph(device);
            case FILTER: return new FilterGraph(device);
            case GATE: return new GateGraph(device);
            case LIMITER: return new LimiterGraph(device);
            case TRANSIENT: return new TransientGraph(device);
            case PLAIN_FILTER: return new PlainFilterGraph(device);
            case OVER: return new OverGraph(device);
            default: return null;
        }
    }
}
